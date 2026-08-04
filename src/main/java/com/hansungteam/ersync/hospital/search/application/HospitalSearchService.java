package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttempt;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferEvent;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferEventType;
import com.hansungteam.ersync.hospital.search.domain.HospitalSearchRound;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferEventRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalSearchRoundRepository;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 이송 요청의 병원 후보를 선택하고 병원별 제안을 안전하게 생성합니다. */
@Service
@RequiredArgsConstructor
public class HospitalSearchService {

    private static final String ATTEMPT_AGGREGATE = "HOSPITAL_DISPATCH_ATTEMPT";
    private static final String OFFER_AGGREGATE = "HOSPITAL_OFFER";
    private static final String REQUEST_AGGREGATE = "TRANSPORT_REQUEST";

    private final HospitalDispatchAttemptRepository attemptRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final HospitalSearchRoundRepository roundRepository;
    private final HospitalOfferRepository offerRepository;
    private final HospitalOfferEventRepository offerEventRepository;
    private final HospitalProfileRepository hospitalProfileRepository;
    private final RealtimeOutboxEventRepository outboxEventRepository;
    private final HaversineDistanceCalculator distanceCalculator;
    private final HospitalSearchPolicy policy;
    private final AuditService auditService;
    private final Clock clock;

    /** 요청 생성과 같은 트랜잭션에 유실되지 않는 최초 탐색 작업을 예약합니다. */
    public HospitalDispatchAttempt initialize(TransportRequest transportRequest, Instant startedAt) {
        HospitalDispatchAttempt attempt = HospitalDispatchAttempt.initial(transportRequest, startedAt);
        attempt.scheduleNextExpansion(0, false, startedAt);
        attemptRepository.save(attempt);
        auditService.record(
                AuditAction.HOSPITAL_SEARCH_STARTED,
                transportRequest.getOwnerAccount(),
                transportRequest.getOrganization(),
                ATTEMPT_AGGREGATE,
                attempt.getPublicId(),
                startedAt
        );
        return attempt;
    }

    /** scheduler가 고른 작업 하나를 잠근 뒤 최초 탐색을 수행합니다. */
    @Transactional
    public void processDueAttempt(Long attemptId) {
        HospitalDispatchAttempt scoped = attemptRepository.findById(attemptId).orElse(null);
        if (scoped == null) {
            return;
        }
        if (transportRequestRepository.findLockedById(scoped.getTransportRequest().getId()).isEmpty()) {
            return;
        }
        HospitalDispatchAttempt attempt = attemptRepository.findLockedById(attemptId).orElse(null);
        if (attempt == null
                || attempt.getStatus() != HospitalDispatchAttemptStatus.SEARCHING
                || attempt.getNextExpansionAt() == null
                || attempt.getNextExpansionAt().isAfter(clock.instant())) {
            return;
        }
        if (attempt.getTransportRequest().getStatus()
                != com.hansungteam.ersync.transport.domain.TransportRequestStatus.SEARCHING) {
            return;
        }
        if (attempt.getCurrentRadiusKm() == 0) {
            performInitialSearch(attempt, clock.instant());
        } else if (attempt.getCurrentRadiusKm() < policy.maximumRadiusKm()) {
            expandSearch(attempt, clock.instant());
        } else {
            closeFinalResponseWindow(attempt, clock.instant());
        }
    }

    /** 최대 반경의 마지막 미결 제안이 거절되면 대기 없이 후보 소진을 확정합니다. */
    public void exhaustIfMaximumRadiusAllRejected(
            HospitalDispatchAttempt attempt,
            TransportRequest transportRequest,
            Instant decidedAt
    ) {
        if (attempt.getStatus() != HospitalDispatchAttemptStatus.SEARCHING
                || attempt.getCurrentRadiusKm() != policy.maximumRadiusKm()) {
            return;
        }
        List<HospitalOffer> offers = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId());
        boolean anyPendingOrAccepted = offers.stream().anyMatch(offer ->
                offer.getStatus() == com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus.PENDING
                        || offer.getStatus()
                        == com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus.ACCEPTED
        );
        if (!offers.isEmpty() && !anyPendingOrAccepted) {
            exhaust(attempt, transportRequest, decidedAt);
        }
    }

    private void performInitialSearch(HospitalDispatchAttempt attempt, Instant evaluatedAt) {
        TransportRequest transportRequest = attempt.getTransportRequest();
        List<HospitalCandidate> candidates = hospitalProfileRepository.findEligibleForNewRequests().stream()
                .map(profile -> new HospitalCandidate(
                        profile,
                        distanceCalculator.meters(
                                transportRequest.getOriginLatitude(),
                                transportRequest.getOriginLongitude(),
                                profile.getLatitude(),
                                profile.getLongitude()
                        )
                ))
                .sorted(Comparator.comparingLong(HospitalCandidate::distanceMeters)
                        .thenComparing(candidate -> candidate.profile().getPublicId()))
                .toList();

        int selectedRadiusKm = selectInitialRadius(candidates);
        List<HospitalCandidate> selectedCandidates = candidatesWithin(candidates, selectedRadiusKm);
        boolean candidateShortage = selectedCandidates.size() < policy.minimumCandidateCount();
        Instant responseDeadlineAt = selectedCandidates.isEmpty()
                ? null
                : evaluatedAt.plus(policy.responseWindow());

        HospitalSearchRound selectedRound = recordInitialRounds(
                attempt,
                candidates,
                selectedRadiusKm,
                selectedCandidates.size(),
                evaluatedAt,
                responseDeadlineAt
        );
        attempt.scheduleNextExpansion(selectedRadiusKm, candidateShortage, responseDeadlineAt);

        for (HospitalCandidate candidate : selectedCandidates) {
            createOffer(transportRequest, attempt, selectedRound, candidate, evaluatedAt);
        }

        if (selectedCandidates.isEmpty()) {
            exhaustWithoutCandidates(attempt, transportRequest, evaluatedAt);
        }
    }

    private int selectInitialRadius(List<HospitalCandidate> candidates) {
        int radiusKm = policy.initialRadiusKm();
        while (radiusKm < policy.maximumRadiusKm()
                && candidatesWithin(candidates, radiusKm).size() < policy.minimumCandidateCount()) {
            radiusKm = Math.min(radiusKm + policy.radiusIncrementKm(), policy.maximumRadiusKm());
        }
        return radiusKm;
    }

    private HospitalSearchRound recordInitialRounds(
            HospitalDispatchAttempt attempt,
            List<HospitalCandidate> candidates,
            int selectedRadiusKm,
            int selectedCandidateCount,
            Instant evaluatedAt,
            Instant responseDeadlineAt
    ) {
        HospitalSearchRound selectedRound = null;
        for (int radiusKm = policy.initialRadiusKm();
                radiusKm <= selectedRadiusKm;
                radiusKm += policy.radiusIncrementKm()) {
            int candidateCount = candidatesWithin(candidates, radiusKm).size();
            boolean selected = radiusKm == selectedRadiusKm;
            HospitalSearchRound round = HospitalSearchRound.record(
                    attempt,
                    radiusKm,
                    candidateCount,
                    selected ? selectedCandidateCount : 0,
                    evaluatedAt,
                    selected ? responseDeadlineAt : null
            );
            roundRepository.save(round);
            if (selected) {
                selectedRound = round;
            } else {
                auditService.record(
                        AuditAction.HOSPITAL_SEARCH_EXPANDED,
                        attempt.getTransportRequest().getOwnerAccount(),
                        attempt.getTransportRequest().getOrganization(),
                        ATTEMPT_AGGREGATE,
                        attempt.getPublicId(),
                        evaluatedAt
                );
            }
        }
        if (selectedRound == null) {
            throw new IllegalStateException("Selected hospital search round was not recorded");
        }
        return selectedRound;
    }

    private void createOffer(
            TransportRequest transportRequest,
            HospitalDispatchAttempt attempt,
            HospitalSearchRound round,
            HospitalCandidate candidate,
            Instant offeredAt
    ) {
        HospitalOffer offer = offerRepository.save(HospitalOffer.offer(
                transportRequest,
                attempt,
                round,
                candidate.profile(),
                candidate.distanceMeters(),
                offeredAt
        ));
        offerEventRepository.save(HospitalOfferEvent.record(
                offer,
                HospitalOfferEventType.OFFERED,
                null,
                null,
                null,
                null,
                offeredAt
        ));
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.TRANSPORT_REQUEST_RECEIVED,
                RealtimeAudienceType.ORGANIZATION,
                candidate.profile().getOrganization().getPublicId(),
                OFFER_AGGREGATE,
                offer.getPublicId(),
                offeredAt
        ));
        auditService.record(
                AuditAction.HOSPITAL_OFFER_CREATED,
                transportRequest.getOwnerAccount(),
                transportRequest.getOrganization(),
                OFFER_AGGREGATE,
                offer.getPublicId(),
                offeredAt
        );
    }

    private void exhaustWithoutCandidates(
            HospitalDispatchAttempt attempt,
            TransportRequest transportRequest,
            Instant exhaustedAt
    ) {
        attempt.exhaust(exhaustedAt);
        transportRequest.markCandidatesExhausted();
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.HOSPITAL_SEARCH_EXHAUSTED,
                RealtimeAudienceType.ACCOUNT,
                transportRequest.getOwnerAccount().getPublicId(),
                REQUEST_AGGREGATE,
                transportRequest.getPublicId(),
                exhaustedAt
        ));
        auditService.record(
                AuditAction.HOSPITAL_SEARCH_EXHAUSTED,
                transportRequest.getOwnerAccount(),
                transportRequest.getOrganization(),
                ATTEMPT_AGGREGATE,
                attempt.getPublicId(),
                exhaustedAt
        );
    }

    private void expandSearch(HospitalDispatchAttempt attempt, Instant evaluatedAt) {
        TransportRequest transportRequest = attempt.getTransportRequest();
        int expandedRadiusKm = Math.min(
                attempt.getCurrentRadiusKm() + policy.radiusIncrementKm(),
                policy.maximumRadiusKm()
        );
        List<HospitalOffer> existingOffers = offerRepository
                .findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId());
        Set<Long> offeredHospitalIds = new HashSet<>();
        for (HospitalOffer offer : existingOffers) {
            offeredHospitalIds.add(offer.getHospitalProfile().getId());
        }

        List<HospitalCandidate> eligibleCandidates = candidatesFor(transportRequest);
        List<HospitalCandidate> newCandidates = candidatesWithin(eligibleCandidates, expandedRadiusKm).stream()
                .filter(candidate -> !offeredHospitalIds.contains(candidate.profile().getId()))
                .toList();
        Instant responseDeadlineAt = evaluatedAt.plus(policy.responseWindow());
        HospitalSearchRound round = roundRepository.save(HospitalSearchRound.record(
                attempt,
                expandedRadiusKm,
                candidatesWithin(eligibleCandidates, expandedRadiusKm).size(),
                newCandidates.size(),
                evaluatedAt,
                responseDeadlineAt
        ));
        boolean candidateShortage = existingOffers.size() + newCandidates.size()
                < policy.minimumCandidateCount();
        attempt.scheduleNextExpansion(expandedRadiusKm, candidateShortage, responseDeadlineAt);

        for (HospitalCandidate candidate : newCandidates) {
            createOffer(transportRequest, attempt, round, candidate, evaluatedAt);
        }
        auditService.record(
                AuditAction.HOSPITAL_SEARCH_EXPANDED,
                null,
                null,
                ATTEMPT_AGGREGATE,
                attempt.getPublicId(),
                evaluatedAt
        );

        if (expandedRadiusKm == policy.maximumRadiusKm()
                && existingOffers.isEmpty()
                && newCandidates.isEmpty()) {
            exhaustWithoutCandidates(attempt, transportRequest, evaluatedAt);
        }
    }

    private void closeFinalResponseWindow(HospitalDispatchAttempt attempt, Instant closedAt) {
        TransportRequest transportRequest = attempt.getTransportRequest();
        List<HospitalOffer> offers = offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId());
        for (HospitalOffer offer : offers) {
            if (offer.getStatus() != com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus.PENDING) {
                continue;
            }
            offer.markNoResponse(closedAt);
            offerEventRepository.save(HospitalOfferEvent.record(
                    offer,
                    HospitalOfferEventType.NO_RESPONSE,
                    null,
                    null,
                    null,
                    null,
                    closedAt
            ));
            outboxEventRepository.save(RealtimeOutboxEvent.create(
                    RealtimeEventType.HOSPITAL_OFFER_NO_RESPONSE,
                    RealtimeAudienceType.ACCOUNT,
                    transportRequest.getOwnerAccount().getPublicId(),
                    OFFER_AGGREGATE,
                    offer.getPublicId(),
                    closedAt
            ));
            auditService.record(
                    AuditAction.HOSPITAL_OFFER_NO_RESPONSE,
                    null,
                    null,
                    OFFER_AGGREGATE,
                    offer.getPublicId(),
                    closedAt
            );
        }
        exhaust(attempt, transportRequest, closedAt);
    }

    private void exhaust(
            HospitalDispatchAttempt attempt,
            TransportRequest transportRequest,
            Instant exhaustedAt
    ) {
        attempt.exhaust(exhaustedAt);
        transportRequest.markCandidatesExhausted();
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.HOSPITAL_SEARCH_EXHAUSTED,
                RealtimeAudienceType.ACCOUNT,
                transportRequest.getOwnerAccount().getPublicId(),
                REQUEST_AGGREGATE,
                transportRequest.getPublicId(),
                exhaustedAt
        ));
        auditService.record(
                AuditAction.HOSPITAL_SEARCH_EXHAUSTED,
                null,
                null,
                ATTEMPT_AGGREGATE,
                attempt.getPublicId(),
                exhaustedAt
        );
    }

    private List<HospitalCandidate> candidatesFor(TransportRequest transportRequest) {
        return hospitalProfileRepository.findEligibleForNewRequests().stream()
                .map(profile -> new HospitalCandidate(
                        profile,
                        distanceCalculator.meters(
                                transportRequest.getOriginLatitude(),
                                transportRequest.getOriginLongitude(),
                                profile.getLatitude(),
                                profile.getLongitude()
                        )
                ))
                .sorted(Comparator.comparingLong(HospitalCandidate::distanceMeters)
                        .thenComparing(candidate -> candidate.profile().getPublicId()))
                .toList();
    }

    private List<HospitalCandidate> candidatesWithin(List<HospitalCandidate> candidates, int radiusKm) {
        long radiusMeters = radiusKm * 1_000L;
        return candidates.stream()
                .filter(candidate -> candidate.distanceMeters() <= radiusMeters)
                .toList();
    }

    private record HospitalCandidate(HospitalProfile profile, long distanceMeters) {
    }
}
