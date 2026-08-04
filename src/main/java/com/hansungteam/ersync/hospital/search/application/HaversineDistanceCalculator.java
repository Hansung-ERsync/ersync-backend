package com.hansungteam.ersync.hospital.search.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 두 위·경도 사이의 대권거리를 미터 단위로 계산합니다. */
@Component
public class HaversineDistanceCalculator {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8D;

    public long meters(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude
    ) {
        double originLatitudeRadians = Math.toRadians(originLatitude.doubleValue());
        double destinationLatitudeRadians = Math.toRadians(destinationLatitude.doubleValue());
        double latitudeDifference = destinationLatitudeRadians - originLatitudeRadians;
        double longitudeDifference = Math.toRadians(
                destinationLongitude.doubleValue() - originLongitude.doubleValue()
        );

        double haversine = square(Math.sin(latitudeDifference / 2D))
                + Math.cos(originLatitudeRadians)
                * Math.cos(destinationLatitudeRadians)
                * square(Math.sin(longitudeDifference / 2D));
        double centralAngle = 2D * Math.atan2(Math.sqrt(haversine), Math.sqrt(1D - haversine));
        return Math.round(EARTH_RADIUS_METERS * centralAngle);
    }

    private double square(double value) {
        return value * value;
    }
}
