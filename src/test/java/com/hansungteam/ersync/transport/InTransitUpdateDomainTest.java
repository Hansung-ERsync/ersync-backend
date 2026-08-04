package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.transport.domain.Avpu;
import com.hansungteam.ersync.transport.domain.ConsciousnessAssessment;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.TransportCurrentLocation;
import com.hansungteam.ersync.transport.domain.VitalSignSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InTransitUpdateDomainTest {

    @Test
    void currentLocationRejectsOlderCaptureButReplacesSameOrNewerCapture() {
        Instant receivedAt = Instant.parse("2026-08-04T01:00:00Z");
        TransportCurrentLocation location = TransportCurrentLocation.create(
                null,
                new BigDecimal("37.5000000"),
                new BigDecimal("127.0000000"),
                receivedAt.minusSeconds(1),
                receivedAt
        );

        boolean older = location.replaceIfCurrent(
                new BigDecimal("36.0000000"),
                new BigDecimal("126.0000000"),
                receivedAt.minusSeconds(2),
                receivedAt.plusSeconds(1)
        );
        boolean same = location.replaceIfCurrent(
                new BigDecimal("37.6000000"),
                new BigDecimal("127.1000000"),
                receivedAt.minusSeconds(1),
                receivedAt.plusSeconds(2)
        );

        assertThat(older).isFalse();
        assertThat(same).isTrue();
        assertThat(location.getLatitude()).isEqualByComparingTo("37.6000000");
        assertThat(location.getLastReceivedAt()).isEqualTo(receivedAt.plusSeconds(2));
    }

    @Test
    void snapshotKeepsNewerClinicalTimeAndUsesReceiptTimeAsTieBreaker() {
        Instant initialReceived = Instant.parse("2026-08-04T01:00:00Z");
        Instant initialClinical = initialReceived.minusSeconds(10);
        VitalSignSet initialVital = VitalSignSet.create(null, initialClinical, initialReceived, initialReceived, null);
        ConsciousnessAssessment consciousness = ConsciousnessAssessment.create(
                null, Avpu.A, null, null, initialClinical, initialReceived, initialReceived, null
        );
        PreKtasAssessment preKtas = PreKtasAssessment.create(
                null, PreKtasClassificationStatus.COMPLETED, 3, null, null,
                initialClinical, "DEV_UNCONFIRMED", initialReceived, initialReceived, null
        );
        CurrentPatientSnapshot snapshot = CurrentPatientSnapshot.create(
                null, null, null, preKtas, consciousness, initialVital, List.of(),
                "ERSYNC_MVP_1.0", initialReceived, initialReceived
        );

        VitalSignSet lateOlder = VitalSignSet.create(
                null, initialClinical.minusSeconds(1), initialReceived.plusSeconds(1),
                initialReceived.plusSeconds(1), null
        );
        VitalSignSet sameClinicalLaterReceipt = VitalSignSet.create(
                null, initialClinical, initialReceived.plusSeconds(2), initialReceived.plusSeconds(2), null
        );
        VitalSignSet sameClinicalAndReceiptProcessedLater = VitalSignSet.create(
                null, initialClinical, initialReceived.plusSeconds(2), initialReceived.plusSeconds(2), null
        );

        assertThat(snapshot.advanceVitalSigns(lateOlder)).isFalse();
        assertThat(snapshot.getLatestVitalSignSet()).isSameAs(initialVital);
        assertThat(snapshot.advanceVitalSigns(sameClinicalLaterReceipt)).isTrue();
        assertThat(snapshot.getLatestVitalSignSet()).isSameAs(sameClinicalLaterReceipt);
        assertThat(snapshot.advanceVitalSigns(sameClinicalAndReceiptProcessedLater)).isTrue();
        assertThat(snapshot.getLatestVitalSignSet()).isSameAs(sameClinicalAndReceiptProcessedLater);
        assertThat(snapshot.getLastClinicalUpdateAt()).isEqualTo(initialReceived.plusSeconds(2));
    }
}
