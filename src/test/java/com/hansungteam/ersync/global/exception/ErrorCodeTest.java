package com.hansungteam.ersync.global.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void errorCodesAreUniqueAndComplete() {
        Set<String> uniqueCodes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .collect(Collectors.toSet());

        assertThat(uniqueCodes).hasSize(ErrorCode.values().length);
        assertThat(Arrays.asList(ErrorCode.values())).allSatisfy(errorCode -> {
            assertThat(errorCode.getCode()).isNotBlank();
            assertThat(errorCode.getMessage()).isNotBlank();
            assertThat(errorCode.getStatus()).isNotNull();
        });
    }
}
