package com.hansungteam.ersync.hospital.search;

import com.hansungteam.ersync.hospital.search.application.HaversineDistanceCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void sameCoordinateIsZeroMeters() {
        long meters = calculator.meters(
                new BigDecimal("37.5821000"),
                new BigDecimal("127.0105000"),
                new BigDecimal("37.5821000"),
                new BigDecimal("127.0105000")
        );

        assertThat(meters).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAboutOneHundredElevenKilometers() {
        long meters = calculator.meters(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO
        );

        assertThat(meters).isBetween(111_190L, 111_200L);
    }
}
