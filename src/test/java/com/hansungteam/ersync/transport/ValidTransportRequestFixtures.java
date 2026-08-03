package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest;
import com.hansungteam.ersync.transport.domain.AgeStatus;
import com.hansungteam.ersync.transport.domain.Avpu;
import com.hansungteam.ersync.transport.domain.OccurrenceType;
import com.hansungteam.ersync.transport.domain.OnsetTimeStatus;
import com.hansungteam.ersync.transport.domain.OriginSource;
import com.hansungteam.ersync.transport.domain.PatientSex;
import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.Symptom;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import com.hansungteam.ersync.transport.domain.VitalSignState;
import com.hansungteam.ersync.transport.domain.VitalSignType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class ValidTransportRequestFixtures {

    private static final Instant CLINICAL_TIME = Instant.parse("2026-08-03T10:00:00Z");
    private static final Instant ENTERED_TIME = Instant.parse("2026-08-03T10:01:00Z");

    private ValidTransportRequestFixtures() {
    }

    public static CreateTransportRequestRequest request() {
        return new CreateTransportRequestRequest(
                "ERSYNC_MVP_1.0",
                new CreateTransportRequestRequest.OriginInput(
                        new BigDecimal("37.5821000"),
                        new BigDecimal("127.0105000"),
                        OriginSource.GPS
                ),
                new CreateTransportRequestRequest.PatientInput(AgeStatus.ESTIMATED, 45, PatientSex.UNKNOWN),
                new CreateTransportRequestRequest.IncidentInput(
                        OccurrenceType.DISEASE,
                        null,
                        null,
                        Set.of(),
                        Symptom.CHEST_PAIN,
                        null,
                        Set.of(Symptom.DYSPNEA),
                        OnsetTimeStatus.ESTIMATED,
                        CLINICAL_TIME,
                        ENTERED_TIME
                ),
                new CreateTransportRequestRequest.PreKtasInput(
                        PreKtasClassificationStatus.COMPLETED,
                        2,
                        null,
                        null,
                        CLINICAL_TIME,
                        "DEV_UNCONFIRMED",
                        ENTERED_TIME
                ),
                new CreateTransportRequestRequest.ConsciousnessInput(
                        Avpu.A,
                        null,
                        null,
                        CLINICAL_TIME,
                        ENTERED_TIME
                ),
                new CreateTransportRequestRequest.VitalSignsInput(
                        CLINICAL_TIME,
                        ENTERED_TIME,
                        List.of(
                                value(VitalSignType.BLOOD_PRESSURE, "120", "80"),
                                value(VitalSignType.PULSE, "80", null),
                                value(VitalSignType.RESPIRATORY_RATE, "18", null),
                                value(VitalSignType.TEMPERATURE, "36.5", null),
                                value(VitalSignType.SPO2, "98", null)
                        )
                ),
                List.of(new CreateTransportRequestRequest.TreatmentInput(
                        TreatmentType.NONE,
                        null,
                        null,
                        null,
                        ENTERED_TIME
                ))
        );
    }

    private static CreateTransportRequestRequest.VitalSignInput value(
            VitalSignType type,
            String primary,
            String secondary
    ) {
        return new CreateTransportRequestRequest.VitalSignInput(
                type,
                VitalSignState.VALUE,
                new BigDecimal(primary),
                secondary == null ? null : new BigDecimal(secondary),
                null,
                null
        );
    }
}
