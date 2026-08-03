package com.hansungteam.ersync.assessment.protocol.api;

import java.util.List;
import java.util.Map;

/** Flutter가 입력 선택지와 개발 상태를 확인하는 활성 평가 프로토콜 계약입니다. */
public record AssessmentProtocolResponse(
        String version,
        String status,
        String preKtasStandardVersion,
        List<String> requiredSections,
        Map<String, List<String>> enumValues,
        Map<String, String> vitalSignUnits,
        List<String> conditionalRules
) {
}
