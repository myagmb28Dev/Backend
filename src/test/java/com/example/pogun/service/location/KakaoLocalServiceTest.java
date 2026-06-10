package com.example.pogun.service.location;

import com.example.pogun.config.web.KakaoLocalProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.location.RegionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoLocalServiceTest {

    @Test
    void resolveRegion_returnsDeterministicFallbackWhenApiKeyMissing() {
        KakaoLocalProperties properties = new KakaoLocalProperties();
        properties.setBaseUrl("https://dapi.kakao.com");

        KakaoLocalService service = new KakaoLocalService(properties, WebClient.builder());

        RegionResponse response = service.resolveRegion(127.1, 37.4);

        assertThat(response.regionType()).isEqualTo("H");
        assertThat(response.addressName()).isNotBlank();
        assertThat(response.region1DepthName()).isNotBlank();
        assertThat(response.region2DepthName()).isNotBlank();
        assertThat(response.region3DepthName()).isNotBlank();
    }

    @Test
    void resolveRegion_prefersAdministrativeRegionDocument() {
        KakaoLocalProperties properties = new KakaoLocalProperties();
        properties.setBaseUrl("https://dapi.kakao.com");
        properties.setRestApiKey("test-key");

        String body = """
                {
                  "meta": { "total_count": 2 },
                  "documents": [
                    {
                      "region_type": "B",
                      "address_name": "Road Address",
                      "region_1depth_name": "Road 1",
                      "region_2depth_name": "Road 2",
                      "region_3depth_name": "Road 3"
                    },
                    {
                      "region_type": "H",
                      "address_name": "Hang Address",
                      "region_1depth_name": "Hang 1",
                      "region_2depth_name": "Hang 2",
                      "region_3depth_name": "Hang 3"
                    }
                  ]
                }
                """;

        KakaoLocalService service = new KakaoLocalService(
                properties,
                WebClient.builder().exchangeFunction(jsonResponse(HttpStatus.OK, body))
        );

        RegionResponse response = service.resolveRegion(127.1, 37.4);

        assertThat(response).isEqualTo(new RegionResponse("H", "Hang Address", "Hang 1", "Hang 2", "Hang 3"));
    }

    @Test
    void resolveRegion_throwsBadRequestWhenNoDocumentsReturned() {
        KakaoLocalProperties properties = new KakaoLocalProperties();
        properties.setBaseUrl("https://dapi.kakao.com");
        properties.setRestApiKey("test-key");

        KakaoLocalService service = new KakaoLocalService(
                properties,
                WebClient.builder().exchangeFunction(jsonResponse(HttpStatus.OK, """
                        {
                          "meta": { "total_count": 0 },
                          "documents": []
                        }
                        """))
        );

        assertThatThrownBy(() -> service.resolveRegion(127.1, 37.4))
                .isInstanceOf(ApiException.class)
                .satisfies(throwable -> {
                    ApiException exception = (ApiException) throwable;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("REGION_NOT_FOUND");
                });
    }

    @Test
    void resolveRegion_wrapsRemoteClientFailure() {
        KakaoLocalProperties properties = new KakaoLocalProperties();
        properties.setBaseUrl("https://dapi.kakao.com");
        properties.setRestApiKey("test-key");

        KakaoLocalService service = new KakaoLocalService(
                properties,
                WebClient.builder().exchangeFunction(request -> Mono.error(
                        WebClientResponseException.create(
                                500,
                                "boom",
                                HttpHeaders.EMPTY,
                                new byte[0],
                                null
                        )))
        );

        assertThatThrownBy(() -> service.resolveRegion(127.1, 37.4))
                .isInstanceOf(ApiException.class)
                .satisfies(throwable -> {
                    ApiException exception = (ApiException) throwable;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getCode()).isEqualTo("KAKAO_LOCAL_REQUEST_FAILED");
                });
    }

    private ExchangeFunction jsonResponse(HttpStatus status, String body) {
        return request -> Mono.just(
                ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()
        );
    }
}
