package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 이송 취소 사유와 OTHER 상세 입력입니다. */
public record CancelTransportRequestRequest(
        @NotNull TransportCancellationReason reason,
        @Size(max = 200) String detail
) {
}
