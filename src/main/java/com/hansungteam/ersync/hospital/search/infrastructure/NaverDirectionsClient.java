package com.hansungteam.ersync.hospital.search.infrastructure;

import com.hansungteam.ersync.hospital.search.application.PermanentRouteEstimateException;
import com.hansungteam.ersync.hospital.search.application.RouteEstimate;
import com.hansungteam.ersync.hospital.search.application.RouteEstimateProvider;
import com.hansungteam.ersync.hospital.search.application.TemporaryRouteEstimateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/** 정확한 좌표와 자격정보를 로그에 남기지 않는 네이버 Directions 5 클라이언트입니다. */
@Component
public class NaverDirectionsClient implements RouteEstimateProvider {

    private static final String CLIENT_ID_HEADER = "X-NCP-APIGW-API-KEY-ID";
    private static final String CLIENT_SECRET_HEADER = "X-NCP-APIGW-API-KEY";

    private final RestClient restClient;
    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;

    public NaverDirectionsClient(
            @Value("${ersync.maps.naver.enabled:false}") boolean enabled,
            @Value("${ersync.maps.naver.base-url:https://maps.apigw.ntruss.com}") String baseUrl,
            @Value("${ersync.maps.naver.client-id:}") String clientId,
            @Value("${ersync.maps.naver.client-secret:}") String clientSecret,
            @Value("${ersync.maps.naver.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${ersync.maps.naver.read-timeout:PT3S}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.enabled = enabled;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public RouteEstimate estimate(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude
    ) {
        if (!enabled || clientId.isBlank() || clientSecret.isBlank()) {
            throw new PermanentRouteEstimateException("Naver Directions is disabled or not configured");
        }
        try {
            NaverDirectionsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/map-direction/v1/driving")
                            .queryParam("start", coordinate(originLongitude, originLatitude))
                            .queryParam("goal", coordinate(destinationLongitude, destinationLatitude))
                            .queryParam("option", "traoptimal")
                            .build())
                    .header(CLIENT_ID_HEADER, clientId)
                    .header(CLIENT_SECRET_HEADER, clientSecret)
                    .retrieve()
                    .body(NaverDirectionsResponse.class);
            return requireEstimate(response);
        } catch (ResourceAccessException exception) {
            throw new TemporaryRouteEstimateException("Naver Directions connection failed", exception);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 429 || status >= 500) {
                throw new TemporaryRouteEstimateException("Naver Directions temporary response", exception);
            }
            throw new PermanentRouteEstimateException("Naver Directions rejected the request", exception);
        } catch (RestClientException exception) {
            throw new PermanentRouteEstimateException("Naver Directions returned an invalid response", exception);
        }
    }

    private RouteEstimate requireEstimate(NaverDirectionsResponse response) {
        if (response == null
                || response.code() == null
                || response.code() != 0
                || response.route() == null
                || response.route().traoptimal() == null
                || response.route().traoptimal().isEmpty()
                || response.route().traoptimal().getFirst().summary() == null) {
            throw new PermanentRouteEstimateException("Naver Directions returned no usable route");
        }
        Summary summary = response.route().traoptimal().getFirst().summary();
        if (summary.distance() == null
                || summary.duration() == null
                || summary.distance() < 0
                || summary.duration() < 0) {
            throw new PermanentRouteEstimateException("Naver Directions returned an invalid summary");
        }
        return new RouteEstimate(summary.distance(), (summary.duration() + 999L) / 1_000L);
    }

    private String coordinate(BigDecimal longitude, BigDecimal latitude) {
        return longitude.toPlainString() + "," + latitude.toPlainString();
    }

    record NaverDirectionsResponse(Integer code, Route route) {
    }

    record Route(List<Path> traoptimal) {
    }

    record Path(Summary summary) {
    }

    record Summary(Long distance, Long duration) {
    }
}
