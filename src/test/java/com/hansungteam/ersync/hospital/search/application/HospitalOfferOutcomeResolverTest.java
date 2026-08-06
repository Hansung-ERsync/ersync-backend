package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.api.HospitalOutcome;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HospitalOfferOutcomeResolverTest {

    private static final Instant RESPONDED_AT = Instant.parse("2026-08-06T07:10:11Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-08-06T07:12:13Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-08-06T07:14:15Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-06T07:16:17Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-08-06T07:18:19Z");
    private static final Instant DESTINATION_CHANGED_AT = Instant.parse("2026-08-06T07:20:21Z");

    private final HospitalOfferOutcomeResolver resolver = new HospitalOfferOutcomeResolver();

    @Test
    void resolvesActivePendingAcceptedAndNotSelectedOffers() {
        assertOutcome(
                facts(TransportRequestStatus.SEARCHING, HospitalOfferStatus.PENDING, false, null),
                HospitalOutcome.AWAITING_RESPONSE,
                null
        );
        assertOutcome(
                facts(TransportRequestStatus.ACCEPTED_AVAILABLE, HospitalOfferStatus.ACCEPTED, false, null),
                HospitalOutcome.ACCEPTED,
                RESPONDED_AT
        );
        assertOutcome(
                facts(
                        TransportRequestStatus.EN_ROUTE,
                        HospitalOfferStatus.ACCEPTED,
                        false,
                        DESTINATION_CHANGED_AT
                ),
                HospitalOutcome.NOT_SELECTED,
                DESTINATION_CHANGED_AT
        );
    }

    @Test
    void preservesRejectedNoResponseAndWithdrawnResultsAfterRequestCompletion() {
        assertOutcome(
                facts(TransportRequestStatus.COMPLETED, HospitalOfferStatus.REJECTED, false, null),
                HospitalOutcome.REJECTED,
                RESPONDED_AT
        );
        assertOutcome(
                facts(TransportRequestStatus.COMPLETED, HospitalOfferStatus.NO_RESPONSE, false, null),
                HospitalOutcome.NO_RESPONSE,
                CLOSED_AT
        );
        assertOutcome(
                facts(TransportRequestStatus.COMPLETED, HospitalOfferStatus.ACCEPTANCE_WITHDRAWN, false, null),
                HospitalOutcome.ACCEPTANCE_WITHDRAWN,
                WITHDRAWN_AT
        );
    }

    @Test
    void distinguishesCompletedDestinationFromOtherHospitalOffers() {
        assertOutcome(
                facts(TransportRequestStatus.COMPLETED, HospitalOfferStatus.ACCEPTED, true, null),
                HospitalOutcome.HANDOFF_COMPLETED_HERE,
                COMPLETED_AT
        );
        assertOutcome(
                facts(TransportRequestStatus.COMPLETED, HospitalOfferStatus.ACCEPTED, false, null),
                HospitalOutcome.COMPLETED_ELSEWHERE,
                COMPLETED_AT
        );
        assertOutcome(
                facts(TransportRequestStatus.COMPLETED, HospitalOfferStatus.PENDING, false, null),
                HospitalOutcome.COMPLETED_ELSEWHERE,
                COMPLETED_AT
        );
    }

    @Test
    void resolvesCancellationWithoutOverwritingEarlierHospitalDecision() {
        assertOutcome(
                facts(TransportRequestStatus.CANCELLED, HospitalOfferStatus.ACCEPTED, false, null),
                HospitalOutcome.TRANSPORT_CANCELLED,
                CANCELLED_AT
        );
        assertOutcome(
                facts(TransportRequestStatus.CANCELLED, HospitalOfferStatus.REJECTED, false, null),
                HospitalOutcome.REJECTED,
                RESPONDED_AT
        );
    }

    @Test
    void fallsBackToClosedAtWhenTerminalTimestampIsMissing() {
        Facts missingCompletedAt = new Facts(
                TransportRequestStatus.COMPLETED,
                HospitalOfferStatus.ACCEPTED,
                true,
                RESPONDED_AT,
                WITHDRAWN_AT,
                CLOSED_AT,
                null,
                CANCELLED_AT,
                null
        );

        assertOutcome(missingCompletedAt, HospitalOutcome.HANDOFF_COMPLETED_HERE, CLOSED_AT);
    }

    private Facts facts(
            TransportRequestStatus requestStatus,
            HospitalOfferStatus offerStatus,
            boolean finalDestination,
            Instant currentDestinationChangedAt
    ) {
        return new Facts(
                requestStatus,
                offerStatus,
                finalDestination,
                RESPONDED_AT,
                WITHDRAWN_AT,
                CLOSED_AT,
                COMPLETED_AT,
                CANCELLED_AT,
                currentDestinationChangedAt
        );
    }

    private void assertOutcome(
            Facts facts,
            HospitalOutcome expectedOutcome,
            Instant expectedProcessedAt
    ) {
        HospitalOfferOutcomeResult result = resolver.resolve(
                facts.requestStatus(),
                facts.offerStatus(),
                facts.finalDestination(),
                facts.respondedAt(),
                facts.withdrawnAt(),
                facts.closedAt(),
                facts.completedAt(),
                facts.cancelledAt(),
                facts.currentDestinationChangedAt()
        );

        assertThat(result.outcome()).isEqualTo(expectedOutcome);
        assertThat(result.processedAt()).isEqualTo(expectedProcessedAt);
    }

    private record Facts(
            TransportRequestStatus requestStatus,
            HospitalOfferStatus offerStatus,
            boolean finalDestination,
            Instant respondedAt,
            Instant withdrawnAt,
            Instant closedAt,
            Instant completedAt,
            Instant cancelledAt,
            Instant currentDestinationChangedAt
    ) {
    }
}
