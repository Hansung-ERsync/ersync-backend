package com.hansungteam.ersync.hospital.search.infrastructure;

import com.hansungteam.ersync.hospital.search.application.PermanentRouteEstimateException;
import com.hansungteam.ersync.hospital.search.application.TemporaryRouteEstimateException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NaverDirectionsClientTest {

    @Test
    void parsesDistanceAndRoundsDurationMillisecondsUpToSeconds() throws Exception {
        AtomicReference<String> requestTarget = new AtomicReference<>();
        AtomicReference<String> clientId = new AtomicReference<>();
        AtomicReference<String> clientSecret = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/map-direction/v1/driving", exchange -> {
            requestTarget.set(exchange.getRequestURI().toString());
            clientId.set(exchange.getRequestHeaders().getFirst("X-NCP-APIGW-API-KEY-ID"));
            clientSecret.set(exchange.getRequestHeaders().getFirst("X-NCP-APIGW-API-KEY"));
            byte[] body = """
                    {
                      "code": 0,
                      "route": {
                        "traoptimal": [
                          {"summary": {"distance": 12345, "duration": 90001}}
                        ]
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            NaverDirectionsClient client = new NaverDirectionsClient(
                    true,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-client-id",
                    "test-client-secret",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1)
            );

            var estimate = client.estimate(
                    new BigDecimal("37.5821000"),
                    new BigDecimal("127.0105000"),
                    new BigDecimal("37.6021000"),
                    new BigDecimal("127.0205000")
            );

            assertThat(estimate.distanceMeters()).isEqualTo(12_345L);
            assertThat(estimate.etaSeconds()).isEqualTo(91L);
            assertThat(clientId.get()).isEqualTo("test-client-id");
            assertThat(clientSecret.get()).isEqualTo("test-client-secret");
            assertThat(requestTarget.get())
                    .contains("option=traoptimal")
                    .contains("start=127.0105000")
                    .contains("goal=127.0205000");
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void temporaryHttpResponsesCanBeRetried(int status) throws Exception {
        try (TestServer server = TestServer.responding(status, "")) {
            assertThatThrownBy(() -> configuredClient(server.baseUrl()).estimate(
                    new BigDecimal("37.5821000"),
                    new BigDecimal("127.0105000"),
                    new BigDecimal("37.6021000"),
                    new BigDecimal("127.0205000")
            )).isInstanceOf(TemporaryRouteEstimateException.class);
        }
    }

    @Test
    void authenticationFailureIsPermanent() throws Exception {
        try (TestServer server = TestServer.responding(401, "")) {
            assertThatThrownBy(() -> configuredClient(server.baseUrl()).estimate(
                    new BigDecimal("37.5821000"),
                    new BigDecimal("127.0105000"),
                    new BigDecimal("37.6021000"),
                    new BigDecimal("127.0205000")
            )).isInstanceOf(PermanentRouteEstimateException.class);
        }
    }

    @Test
    void malformedJsonIsPermanent() throws Exception {
        try (TestServer server = TestServer.responding(200, "{not-json")) {
            assertThatThrownBy(() -> configuredClient(server.baseUrl()).estimate(
                    new BigDecimal("37.5821000"),
                    new BigDecimal("127.0105000"),
                    new BigDecimal("37.6021000"),
                    new BigDecimal("127.0205000")
            )).isInstanceOf(PermanentRouteEstimateException.class);
        }
    }

    @Test
    void connectionFailureCanBeRetried() {
        assertThatThrownBy(() -> configuredClient("http://127.0.0.1:1").estimate(
                new BigDecimal("37.5821000"),
                new BigDecimal("127.0105000"),
                new BigDecimal("37.6021000"),
                new BigDecimal("127.0205000")
        )).isInstanceOf(TemporaryRouteEstimateException.class);
    }

    @Test
    void disabledClientFailsWithoutCallingExternalApi() {
        NaverDirectionsClient client = new NaverDirectionsClient(
                false,
                "http://127.0.0.1:1",
                "",
                "",
                Duration.ofMillis(100),
                Duration.ofMillis(100)
        );

        assertThatThrownBy(() -> client.estimate(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE
        )).isInstanceOf(PermanentRouteEstimateException.class);
    }

    private NaverDirectionsClient configuredClient(String baseUrl) {
        return new NaverDirectionsClient(
                true,
                baseUrl,
                "test-client-id",
                "test-client-secret",
                Duration.ofMillis(200),
                Duration.ofMillis(200)
        );
    }

    private record TestServer(HttpServer server) implements AutoCloseable {

        static TestServer responding(int status, String responseBody) throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/map-direction/v1/driving", exchange -> {
                byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return new TestServer(server);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
