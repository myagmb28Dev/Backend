package com.example.pogun.config.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHostResolverTest {

    @Test
    void resolve_prefersOriginHost() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Origin", "https://paw.gbsw.hs.kr");
        request.addHeader("X-Forwarded-Host", "internal.example:8080");

        assertThat(RequestHostResolver.resolve(request)).isEqualTo("paw.gbsw.hs.kr");
    }

    @Test
    void resolve_usesFirstForwardedHostWithoutPort() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/presence/heartbeat");
        request.addHeader("X-Forwarded-Host", "paw.gbsw.hs.kr:443, internal.local:8080");

        assertThat(RequestHostResolver.resolve(request)).isEqualTo("paw.gbsw.hs.kr");
    }

    @Test
    void resolve_stripsIpv6BracketsFromHostHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/presence/heartbeat");
        request.addHeader("Host", "[::1]:8080");

        assertThat(RequestHostResolver.resolve(request)).isEqualTo("::1");
    }
}
