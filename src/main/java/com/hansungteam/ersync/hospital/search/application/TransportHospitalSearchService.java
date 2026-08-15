package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.search.api.TransportHospitalSearchResponse;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttempt;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** 구급대원의 소유 요청에 한해 병원 탐색과 응답 현황을 제공합니다. */
@Service
@RequiredArgsConstructor
public class TransportHospitalSearchService {

    private final UserAccountRepository userAccountRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final HospitalDispatchAttemptRepository attemptRepository;
    private final HospitalOfferRepository offerRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TransportHospitalSearchResponse status(
            AuthenticatedAccount principal,
            String transportRequestId
    ) {
        UserAccount account = requireParamedic(principal);
        TransportRequest request = requireOwnedRequest(account, transportRequestId);
        HospitalDispatchAttempt attempt = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(transportRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        List<HospitalOffer> offers = offerRepository
                .findByTransportRequestPublicIdOrderByOfferedAtAsc(transportRequestId);
        return new TransportHospitalSearchResponse(
                request.getPublicId(),
                request.getStatus(),
                request.getCurrentDestinationOffer() == null
                        ? null
                        : request.getCurrentDestinationOffer().getPublicId(),
                toAttempt(attempt),
                exhaustionReason(request, offers),
                offers.stream().map(offer -> toOffer(request, offer)).toList(),
                clock.instant()
        );
    }

    private UserAccount requireParamedic(AuthenticatedAccount principal) {
        if (principal.role() != UserRole.PARAMEDIC || principal.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = userAccountRepository.findByPublicId(principal.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.PARAMEDIC
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || !account.getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        return account;
    }

    private TransportRequest requireOwnedRequest(UserAccount account, String transportRequestId) {
        return transportRequestRepository.findByPublicId(transportRequestId)
                .filter(request -> request.getOwnerAccount().getPublicId().equals(account.getPublicId()))
                .filter(request -> request.getOrganization().getPublicId()
                        .equals(account.getOrganization().getPublicId()))
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
    }

    private TransportHospitalSearchResponse.Attempt toAttempt(HospitalDispatchAttempt attempt) {
        return new TransportHospitalSearchResponse.Attempt(
                attempt.getPublicId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getTriggerType(),
                attempt.getCurrentRadiusKm(),
                attempt.isCandidateShortage(),
                attempt.getNextExpansionAt(),
                attempt.getStartedAt(),
                attempt.getEndedAt()
        );
    }

    private TransportHospitalSearchResponse.Offer toOffer(
            TransportRequest request,
            HospitalOffer offer
    ) {
        boolean showContact = offer.getStatus() == HospitalOfferStatus.ACCEPTED
                || (request.getStatus() == TransportRequestStatus.CANDIDATES_EXHAUSTED
                && offer.getStatus() != HospitalOfferStatus.ACCEPTANCE_WITHDRAWN);
        boolean showAcceptedLocation = offer.getStatus() == HospitalOfferStatus.ACCEPTED;
        return new TransportHospitalSearchResponse.Offer(
                offer.getPublicId(),
                offer.getDispatchAttempt().getAttemptNumber(),
                offer.getHospitalNameSnapshot(),
                showContact ? offer.getHospitalContactSnapshot() : null,
                showAcceptedLocation ? offer.getHospitalAddressSnapshot() : null,
                showAcceptedLocation ? offer.getHospitalDetailAddressSnapshot() : null,
                showAcceptedLocation ? offer.getHospitalLatitudeSnapshot() : null,
                showAcceptedLocation ? offer.getHospitalLongitudeSnapshot() : null,
                offer.getStatus(),
                request.hasDestination(offer),
                offer.getStraightLineDistanceMeters(),
                offer.getRouteEstimateStatus(),
                offer.getRouteDistanceMeters(),
                offer.getEtaSeconds(),
                offer.getEtaCalculatedAt(),
                offer.getLastSuccessRouteDistanceMeters(),
                offer.getLastSuccessEtaSeconds(),
                offer.getLastSuccessEtaCalculatedAt(),
                offer.getRejectionReason(),
                offer.getRejectionDetail(),
                offer.getWithdrawalReason(),
                offer.getWithdrawalDetail(),
                offer.getOfferedAt(),
                offer.getRespondedAt(),
                offer.getWithdrawnAt(),
                offer.getClosedAt()
        );
    }

    private String exhaustionReason(TransportRequest request, List<HospitalOffer> offers) {
        if (request.getStatus() != TransportRequestStatus.CANDIDATES_EXHAUSTED) {
            return null;
        }
        if (offers.isEmpty()) {
            return "NO_CANDIDATES";
        }
        if (offers.stream().anyMatch(offer -> offer.getStatus() == HospitalOfferStatus.NO_RESPONSE)) {
            return "NO_RESPONSE_INCLUDED";
        }
        return "ALL_REJECTED";
    }

}
