package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.transport.api.ClinicalTimelineResponse;
import com.hansungteam.ersync.transport.domain.ClinicalRecordType;
import com.hansungteam.ersync.transport.domain.ConsciousnessAssessment;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.domain.TreatmentDetails;
import com.hansungteam.ersync.transport.domain.TreatmentEvent;
import com.hansungteam.ersync.transport.domain.VitalSignSet;
import com.hansungteam.ersync.transport.infrastructure.ClinicalTimelineRepository;
import com.hansungteam.ersync.transport.infrastructure.ClinicalTimelineRow;
import com.hansungteam.ersync.transport.infrastructure.ConsciousnessAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.PreKtasAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import com.hansungteam.ersync.transport.infrastructure.TreatmentEventRepository;
import com.hansungteam.ersync.transport.infrastructure.VitalSignSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 구급대원 소유권과 병원 현재 공개 대상을 확인한 뒤 임상 timeline을 조회합니다. */
@Service
@RequiredArgsConstructor
public class ClinicalTimelineQueryService {

    private final UserAccountRepository userAccountRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final HospitalProfileRepository hospitalProfileRepository;
    private final HospitalOfferRepository hospitalOfferRepository;
    private final CurrentPatientSnapshotRepository snapshotRepository;
    private final ClinicalTimelineRepository timelineRepository;
    private final VitalSignSetRepository vitalSignSetRepository;
    private final ConsciousnessAssessmentRepository consciousnessAssessmentRepository;
    private final PreKtasAssessmentRepository preKtasAssessmentRepository;
    private final TreatmentEventRepository treatmentEventRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ClinicalTimelineResponse ownerTimeline(
            AuthenticatedAccount authenticated,
            String requestId,
            int page,
            int size
    ) {
        UserAccount account = requireAccount(authenticated, UserRole.PARAMEDIC);
        if (account.getOrganization().getType() != OrganizationType.EMS_UNIT) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
        TransportRequest request = transportRequestRepository
                .findByPublicIdAndOwnerAccountPublicId(requestId, account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        return timeline(request, page, size);
    }

    @Transactional(readOnly = true)
    public ClinicalTimelineResponse hospitalTimeline(
            AuthenticatedAccount authenticated,
            String offerId,
            int page,
            int size
    ) {
        UserAccount account = requireAccount(authenticated, UserRole.HOSPITAL_STAFF);
        HospitalProfile profile = hospitalProfileRepository.findByAccountPublicId(account.getPublicId())
                .filter(found -> found.getOrganization().getPublicId().equals(authenticated.organizationId()))
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));
        HospitalOffer offer = hospitalOfferRepository
                .findByPublicIdAndHospitalProfileOrganizationPublicId(
                        offerId, profile.getOrganization().getPublicId()
                )
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        if (!canReadClinical(offer)) {
            throw new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND);
        }
        return timeline(offer.getTransportRequest(), page, size);
    }

    private ClinicalTimelineResponse timeline(TransportRequest request, int page, int size) {
        long offset = (long) page * size;
        if (page < 0 || size < 1 || size > 100 || offset > Integer.MAX_VALUE) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        CurrentPatientSnapshot snapshot = snapshotRepository
                .findByTransportRequestPublicId(request.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        List<ClinicalTimelineRow> rows = timelineRepository.findPage(request.getId(), (int) offset, size);
        long total = timelineRepository.count(request.getId());
        Map<ClinicalRecordType, List<String>> ids = rows.stream().collect(Collectors.groupingBy(
                ClinicalTimelineRow::recordType,
                () -> new EnumMap<>(ClinicalRecordType.class),
                Collectors.mapping(ClinicalTimelineRow::recordPublicId, Collectors.toList())
        ));
        Map<String, VitalSignSet> vitalSigns = byPublicId(
                vitalSignSetRepository.findByPublicIdIn(ids.getOrDefault(ClinicalRecordType.VITAL_SIGNS, List.of())),
                VitalSignSet::getPublicId
        );
        Map<String, ConsciousnessAssessment> consciousness = byPublicId(
                consciousnessAssessmentRepository.findByPublicIdIn(
                        ids.getOrDefault(ClinicalRecordType.CONSCIOUSNESS, List.of())
                ),
                ConsciousnessAssessment::getPublicId
        );
        Map<String, PreKtasAssessment> preKtas = byPublicId(
                preKtasAssessmentRepository.findByPublicIdIn(ids.getOrDefault(ClinicalRecordType.PRE_KTAS, List.of())),
                PreKtasAssessment::getPublicId
        );
        Map<String, TreatmentEvent> treatments = byPublicId(
                treatmentEventRepository.findByPublicIdIn(ids.getOrDefault(ClinicalRecordType.TREATMENT, List.of())),
                TreatmentEvent::getPublicId
        );

        List<ClinicalTimelineResponse.Item> items = rows.stream().map(row -> switch (row.recordType()) {
            case VITAL_SIGNS -> item(vitalSigns.get(row.recordPublicId()), row);
            case CONSCIOUSNESS -> item(consciousness.get(row.recordPublicId()), row);
            case PRE_KTAS -> item(preKtas.get(row.recordPublicId()), row);
            case TREATMENT -> item(treatments.get(row.recordPublicId()), row);
        }).toList();
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new ClinicalTimelineResponse(
                request.getPublicId(), latest(snapshot), items, page, size, total, totalPages, clock.instant()
        );
    }

    private boolean canReadClinical(HospitalOffer offer) {
        TransportRequest request = offer.getTransportRequest();
        if (request.getStatus() == TransportRequestStatus.COMPLETED
                || request.getStatus() == TransportRequestStatus.CANCELLED) {
            return false;
        }
        if (request.getCurrentDestinationOffer() != null) {
            return offer.getStatus() == HospitalOfferStatus.ACCEPTED && request.hasDestination(offer);
        }
        return offer.getStatus() == HospitalOfferStatus.PENDING
                || offer.getStatus() == HospitalOfferStatus.ACCEPTED;
    }

    private UserAccount requireAccount(AuthenticatedAccount authenticated, UserRole expectedRole) {
        if (authenticated.role() != expectedRole || authenticated.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = userAccountRepository.findByPublicId(authenticated.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != expectedRole
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || !account.getOrganization().getPublicId().equals(authenticated.organizationId())) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        return account;
    }

    private ClinicalTimelineResponse.LatestSnapshot latest(CurrentPatientSnapshot snapshot) {
        return new ClinicalTimelineResponse.LatestSnapshot(
                preKtas(snapshot.getLatestPreKtasAssessment()),
                consciousness(snapshot.getLatestConsciousnessAssessment()),
                vitalSigns(snapshot.getLatestVitalSignSet()),
                snapshot.getCurrentTreatments().stream().map(this::treatment).toList(),
                snapshot.getLastClinicalUpdateAt()
        );
    }

    private ClinicalTimelineResponse.Item item(VitalSignSet record, ClinicalTimelineRow row) {
        requireLoaded(record);
        return new ClinicalTimelineResponse.Item(
                row.recordType(), row.recordPublicId(), row.clinicalAt(), record.getEnteredAt(),
                row.serverReceivedAt(), null, null, vitalSigns(record), null
        );
    }

    private ClinicalTimelineResponse.Item item(ConsciousnessAssessment record, ClinicalTimelineRow row) {
        requireLoaded(record);
        return new ClinicalTimelineResponse.Item(
                row.recordType(), row.recordPublicId(), row.clinicalAt(), record.getEnteredAt(),
                row.serverReceivedAt(), null, consciousness(record), null, null
        );
    }

    private ClinicalTimelineResponse.Item item(PreKtasAssessment record, ClinicalTimelineRow row) {
        requireLoaded(record);
        return new ClinicalTimelineResponse.Item(
                row.recordType(), row.recordPublicId(), row.clinicalAt(), record.getEnteredAt(),
                row.serverReceivedAt(), preKtas(record), null, null, null
        );
    }

    private ClinicalTimelineResponse.Item item(TreatmentEvent record, ClinicalTimelineRow row) {
        requireLoaded(record);
        return new ClinicalTimelineResponse.Item(
                row.recordType(), row.recordPublicId(), row.clinicalAt(), record.getEnteredAt(),
                row.serverReceivedAt(), null, null, null, treatment(record)
        );
    }

    private ClinicalTimelineResponse.PreKtas preKtas(PreKtasAssessment record) {
        return new ClinicalTimelineResponse.PreKtas(
                record.getClassificationStatus().name(), record.getLevel(), enumName(record.getExceptionReason()),
                record.getExceptionDetail(), record.getAssessedAt(), record.getStandardVersion()
        );
    }

    private ClinicalTimelineResponse.Consciousness consciousness(ConsciousnessAssessment record) {
        return new ClinicalTimelineResponse.Consciousness(
                record.getAvpu().name(), enumName(record.getUnassessableReason()),
                record.getUnassessableDetail(), record.getObservedAt()
        );
    }

    private ClinicalTimelineResponse.VitalSigns vitalSigns(VitalSignSet record) {
        return new ClinicalTimelineResponse.VitalSigns(
                record.getMeasuredAt(),
                record.getMeasurements().stream().map(measurement -> new ClinicalTimelineResponse.VitalSign(
                        measurement.getMeasurementType().name(), measurement.getState().name(),
                        measurement.getPrimaryValue(), measurement.getSecondaryValue(),
                        enumName(measurement.getUnavailableReason()), measurement.getUnavailableDetail()
                )).toList()
        );
    }

    private ClinicalTimelineResponse.Treatment treatment(TreatmentEvent record) {
        return new ClinicalTimelineResponse.Treatment(
                record.getTreatmentType().name(), enumName(record.getAttemptResult()), record.getPerformedAt(),
                details(record.getDetails())
        );
    }

    private ClinicalTimelineResponse.TreatmentDetails details(TreatmentDetails details) {
        if (details == null) {
            return null;
        }
        return new ClinicalTimelineResponse.TreatmentDetails(
                details.getMethod(), details.getDevice(), details.getFlowRateLpm(), details.getStartedAt(),
                details.getSuccess(), details.getCurrentStatus(), details.getRosc(), details.getRoscAt(),
                details.getShockCount(), details.getFluidName(), details.getAmountMl(),
                details.getMedicationName(), details.getDose(), details.getRoute(), details.getSite(),
                details.getTourniquetUsed(), details.getTourniquetAppliedAt(), details.getLeadType(),
                details.getFindings(), details.getTransmitted(), details.getBirthAt(), details.getDetail()
        );
    }

    private <T> Map<String, T> byPublicId(Collection<T> values, Function<T, String> idExtractor) {
        return values.stream().collect(Collectors.toMap(idExtractor, Function.identity()));
    }

    private void requireLoaded(Object value) {
        if (value == null) {
            throw new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR);
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
