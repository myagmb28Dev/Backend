package com.example.pogun.service.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTrafficLogServiceTest {

    @Test
    void skipsTrafficConfigPathFromInboundLogs() {
        AdminTrafficLogService service = new AdminTrafficLogService();

        service.recordInbound("GET", "/api/admin/traffic/config", 200, 3, "127.0.0.1", "", "");

        assertThat(service.recent(100)).isEmpty();
        assertThat(service.currentErrorCount()).isZero();
    }

    @Test
    void skipsAbsoluteTrafficLogPathWithQueryFromInboundLogs() {
        AdminTrafficLogService service = new AdminTrafficLogService();

        service.recordInbound(
                "GET",
                "https://paw.gbsw.hs.kr/api/admin/traffic/logs?limit=100",
                200,
                4,
                "127.0.0.1",
                "",
                ""
        );

        assertThat(service.recent(100)).isEmpty();
        assertThat(service.currentErrorCount()).isZero();
    }

    @Test
    void skipsTrafficErrorPathFromErrorCount() {
        AdminTrafficLogService service = new AdminTrafficLogService();

        service.recordInbound("GET", "/api/admin/traffic/config", 500, 3, "127.0.0.1", "", "");

        assertThat(service.recent(100, true)).isEmpty();
        assertThat(service.currentErrorCount()).isZero();
    }

    @Test
    void keepsNonTrafficApiInboundLogs() {
        AdminTrafficLogService service = new AdminTrafficLogService();

        service.recordInbound("GET", "/api/admin/dashboard/summary", 200, 5, "127.0.0.1", "", "{}");

        assertThat(service.recent(100)).hasSize(1);
        assertThat(service.recent(100).get(0).get("path")).isEqualTo("/api/admin/dashboard/summary");
    }

    @Test
    void redactsSensitiveQueryParametersFromOutboundLogs() {
        AdminTrafficLogService service = new AdminTrafficLogService();

        service.recordOutbound(
                "GET",
                "https://apis.data.go.kr/1543061/abandonmentPublicService_v2/abandonmentPublic_v2?serviceKey=real-secret&key=firebase-secret&safe=value",
                200,
                8,
                "HttpClient"
        );

        String path = String.valueOf(service.recent(100).get(0).get("path"));
        assertThat(path).contains("serviceKey=%5BREDACTED%5D");
        assertThat(path).contains("key=%5BREDACTED%5D");
        assertThat(path).contains("safe=value");
        assertThat(path).doesNotContain("real-secret", "firebase-secret");
    }

    @Test
    void redactsSensitiveQueryParametersFromInboundLogs() {
        AdminTrafficLogService service = new AdminTrafficLogService();

        service.recordInbound(
                "POST",
                "/api/missing-pets/source?purchaseToken=purchase-secret&token=auth-secret",
                200,
                5,
                "127.0.0.1",
                "",
                "{}"
        );

        String path = String.valueOf(service.recent(100).get(0).get("path"));
        assertThat(path).contains("purchaseToken=%5BREDACTED%5D");
        assertThat(path).contains("token=%5BREDACTED%5D");
        assertThat(path).doesNotContain("purchase-secret", "auth-secret");
    }
}
