package com.example.pogun.config.web;

import com.example.pogun.service.admin.AdminTrafficLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final AdminTrafficLogService adminTrafficLogService;

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder().filter(logOutboundFilter());
    }

    private ExchangeFilterFunction logOutboundFilter() {
        return (request, next) -> {
            long start = System.currentTimeMillis();
            return next.exchange(request)
                    .flatMap(response -> {
                        recordWebClientSuccess(request, response, start);
                        return Mono.just(response);
                    })
                    .onErrorResume(error -> {
                        recordWebClientFailure(request, start);
                        return Mono.error(error);
                    });
        };
    }

    private void recordWebClientSuccess(ClientRequest request, ClientResponse response, long start) {
        long durationMs = System.currentTimeMillis() - start;
        adminTrafficLogService.recordOutbound(
                request.method().name(),
                request.url().toString(),
                response.statusCode().value(),
                durationMs,
                "WebClient"
        );
    }

    private void recordWebClientFailure(ClientRequest request, long start) {
        long durationMs = System.currentTimeMillis() - start;
        adminTrafficLogService.recordOutbound(
                request.method().name(),
                request.url().toString(),
                0,
                durationMs,
                "WebClient"
        );
    }
}
