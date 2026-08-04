package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.transport.api.TransportRequestListResponse;
import com.hansungteam.ersync.transport.api.TransportRequestView;
import com.hansungteam.ersync.transport.domain.TransportLifecycleCommand;
import com.hansungteam.ersync.transport.domain.TransportLifecycleCommandType;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportLifecycleCommandRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 구급대원 본인의 활성·종료·홈 최근 이송을 최소 정보로 조회합니다. */
@Service
@RequiredArgsConstructor
public class TransportRequestQueryService {

    private final UserAccountRepository accountRepository;
    private final TransportRequestRepository requestRepository;
    private final TransportLifecycleCommandRepository commandRepository;

    @Transactional(readOnly = true)
    public TransportRequestListResponse list(
            AuthenticatedAccount principal,
            TransportRequestView view,
            int page,
            int size
    ) {
        UserAccount account = requireParamedic(principal);
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        Page<TransportRequest> result = requestRepository.findPageByOwnerAndStatuses(
                account.getPublicId(), view.statuses(), PageRequest.of(page, size)
        );
        Map<Long, HospitalOffer> cancelledDestinations = cancellationDestinations(result.getContent());
        List<TransportRequestListResponse.Item> items = result.getContent().stream()
                .map(request -> toItem(request, cancelledDestinations.get(request.getId())))
                .toList();
        return new TransportRequestListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private UserAccount requireParamedic(AuthenticatedAccount principal) {
        if (principal.role() != UserRole.PARAMEDIC || principal.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = accountRepository.findByPublicId(principal.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.PARAMEDIC
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || account.getOrganization().getType() != OrganizationType.EMS_UNIT
                || !account.getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
        return account;
    }

    private Map<Long, HospitalOffer> cancellationDestinations(List<TransportRequest> requests) {
        List<Long> cancelledIds = requests.stream()
                .filter(request -> request.getStatus() == TransportRequestStatus.CANCELLED)
                .map(TransportRequest::getId)
                .toList();
        if (cancelledIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, HospitalOffer> result = new HashMap<>();
        for (TransportLifecycleCommand command : commandRepository.findByTransportRequestIdsAndCommandType(
                cancelledIds, TransportLifecycleCommandType.CANCEL
        )) {
            result.put(command.getTransportRequest().getId(), command.getDestinationOffer());
        }
        return result;
    }

    private TransportRequestListResponse.Item toItem(
            TransportRequest request,
            HospitalOffer cancelledDestination
    ) {
        HospitalOffer destination = request.getStatus() == TransportRequestStatus.CANCELLED
                ? cancelledDestination
                : request.getCurrentDestinationOffer();
        return new TransportRequestListResponse.Item(
                request.getPublicId(),
                request.getStatus(),
                destination == null ? null : destination.getHospitalNameSnapshot(),
                request.getCreatedAt(),
                statusUpdatedAt(request),
                request.getHandoffRequestedAt(),
                request.getCompletedAt(),
                request.getCancelledAt(),
                request.getCancellationReason()
        );
    }

    private Instant statusUpdatedAt(TransportRequest request) {
        return switch (request.getStatus()) {
            case HANDOFF_REQUESTED -> fallback(request.getHandoffRequestedAt(), request.getUpdatedAt());
            case COMPLETED -> fallback(request.getCompletedAt(), request.getUpdatedAt());
            case CANCELLED -> fallback(request.getCancelledAt(), request.getUpdatedAt());
            default -> request.getUpdatedAt();
        };
    }

    private Instant fallback(Instant preferred, Instant fallback) {
        return preferred == null ? fallback : preferred;
    }
}
