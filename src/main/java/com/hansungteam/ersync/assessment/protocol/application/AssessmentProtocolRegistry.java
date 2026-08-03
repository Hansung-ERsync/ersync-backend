package com.hansungteam.ersync.assessment.protocol.application;

import com.hansungteam.ersync.assessment.protocol.api.AssessmentProtocolResponse;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.transport.domain.AgeStatus;
import com.hansungteam.ersync.transport.domain.Avpu;
import com.hansungteam.ersync.transport.domain.ConsciousnessUnassessableReason;
import com.hansungteam.ersync.transport.domain.InjuryMechanism;
import com.hansungteam.ersync.transport.domain.InjurySite;
import com.hansungteam.ersync.transport.domain.OccurrenceType;
import com.hansungteam.ersync.transport.domain.OnsetTimeStatus;
import com.hansungteam.ersync.transport.domain.OriginSource;
import com.hansungteam.ersync.transport.domain.PatientSex;
import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.PreKtasExceptionReason;
import com.hansungteam.ersync.transport.domain.Symptom;
import com.hansungteam.ersync.transport.domain.TreatmentAttemptResult;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import com.hansungteam.ersync.transport.domain.VitalSignState;
import com.hansungteam.ersync.transport.domain.VitalSignType;
import com.hansungteam.ersync.transport.domain.VitalSignUnavailableReason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 배포 코드에 포함된 불변 개발용 평가 프로토콜을 제공합니다. */
@Component
public class AssessmentProtocolRegistry {

    private final String activeVersion;
    private final String preKtasStandardVersion;
    private final AssessmentProtocolResponse activeProtocol;

    public AssessmentProtocolRegistry(
            @Value("${ersync.assessment.active-protocol-version}") String activeVersion,
            @Value("${ersync.assessment.pre-ktas-standard-version}") String preKtasStandardVersion
    ) {
        this.activeVersion = activeVersion;
        this.preKtasStandardVersion = preKtasStandardVersion;
        this.activeProtocol = buildResponse();
    }

    public AssessmentProtocolResponse active() {
        return activeProtocol;
    }

    public void requireActive(String requestedVersion) {
        if (!activeVersion.equals(requestedVersion)) {
            throw new CustomException(ErrorCode.PROTOCOL_VERSION_INACTIVE);
        }
    }

    public void requirePreKtasStandardVersion(String requestedVersion) {
        if (!preKtasStandardVersion.equals(requestedVersion)) {
            throw new CustomException(ErrorCode.PROTOCOL_VERSION_INACTIVE);
        }
    }

    private AssessmentProtocolResponse buildResponse() {
        Map<String, List<String>> enumValues = new LinkedHashMap<>();
        enumValues.put("ageStatus", names(AgeStatus.values()));
        enumValues.put("patientSex", names(PatientSex.values()));
        enumValues.put("originSource", names(OriginSource.values()));
        enumValues.put("occurrenceType", names(OccurrenceType.values()));
        enumValues.put("injuryMechanism", names(InjuryMechanism.values()));
        enumValues.put("injurySite", names(InjurySite.values()));
        enumValues.put("symptom", names(Symptom.values()));
        enumValues.put("onsetTimeStatus", names(OnsetTimeStatus.values()));
        enumValues.put("preKtasClassificationStatus", names(PreKtasClassificationStatus.values()));
        enumValues.put("preKtasExceptionReason", names(PreKtasExceptionReason.values()));
        enumValues.put("avpu", names(Avpu.values()));
        enumValues.put("consciousnessUnassessableReason", names(ConsciousnessUnassessableReason.values()));
        enumValues.put("vitalSignType", names(VitalSignType.values()));
        enumValues.put("vitalSignState", names(VitalSignState.values()));
        enumValues.put("vitalSignUnavailableReason", names(VitalSignUnavailableReason.values()));
        enumValues.put("treatmentType", names(TreatmentType.values()));
        enumValues.put("treatmentAttemptResult", names(TreatmentAttemptResult.values()));

        Map<String, String> units = new LinkedHashMap<>();
        units.put("BLOOD_PRESSURE", "mmHg");
        units.put("PULSE", "beats/min");
        units.put("RESPIRATORY_RATE", "breaths/min");
        units.put("TEMPERATURE", "degC");
        units.put("SPO2", "percent");

        return new AssessmentProtocolResponse(
                activeVersion,
                "DEVELOPMENT",
                preKtasStandardVersion,
                List.of("origin", "patient", "incident", "preKtas", "consciousness", "vitalSigns", "treatments"),
                Map.copyOf(enumValues),
                Map.copyOf(units),
                List.of(
                        "EXACT_OR_ESTIMATED_AGE_REQUIRES_VALUE",
                        "NON_DISEASE_REQUIRES_INJURY_INFORMATION",
                        "KNOWN_ONSET_REQUIRES_TIME",
                        "PRE_KTAS_COMPLETED_OR_EMERGENCY_UNFINISHED",
                        "ALL_FIVE_VITAL_SIGNS_EXACTLY_ONCE",
                        "NONE_TREATMENT_MUST_BE_ALONE"
                )
        );
    }

    private List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
