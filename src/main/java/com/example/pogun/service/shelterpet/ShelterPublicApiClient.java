package com.example.pogun.service.shelterpet;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 국가동물보호정보시스템 구조동물 조회 API 호출을 담당한다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(ShelterPublicApiProperties.class)
public class ShelterPublicApiClient {

    private static final String SOURCE = "KOREA_ANIMAL_PROTECTION_API";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ShelterPublicApiProperties properties;

    public ShelterPublicApiClient(ObjectMapper objectMapper, ShelterPublicApiProperties properties) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ShelterPublicApiPage fetchShelterPets(String region, String breed, String status, String sort, int page, int size) {
        JsonNode response = callAbandonmentApi(buildListUri(region, breed, status, page, size));
        JsonNode body = requireSuccessBody(response);
        List<ShelterPublicApiAnimal> items = extractItems(body).stream()
                .map(this::toAnimal)
                .filter(Objects::nonNull)
                .sorted(resolveComparator(sort))
                .toList();
        return new ShelterPublicApiPage(
                parseInt(body.path("pageNo").asText(), page <= 0 ? 1 : page),
                parseInt(body.path("numOfRows").asText(), size),
                parseInt(body.path("totalCount").asText(), items.size()),
                items
        );
    }

    public ShelterPublicApiAnimal fetchShelterPet(String desertionNo) {
        JsonNode response = callAbandonmentApi(buildDetailUri(desertionNo));
        JsonNode body = requireSuccessBody(response);
        List<JsonNode> items = extractItems(body);
        if (items.isEmpty()) {
            throw ApiException.notFound("SHELTER_PET_NOT_FOUND", "해당 외부 공고를 찾을 수 없습니다.");
        }
        ShelterPublicApiAnimal animal = toAnimal(items.get(0));
        if (animal == null) {
            throw ApiException.notFound("SHELTER_PET_NOT_FOUND", "해당 외부 공고를 찾을 수 없습니다.");
        }
        return animal;
    }

    public List<ShelterPublicApiReference> fetchSido() {
        JsonNode response = callPublicApi(buildReferenceUri(properties.getSidoPath(), null, null));
        return extractItems(requireSuccessBody(response)).stream()
                .map(item -> toReference(item, "orgCd", "orgdownNm"))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ShelterPublicApiReference> fetchSigungu(String uprCd) {
        requireText(uprCd, "MISSING_UPR_CD", "시도 코드는 필수입니다.");
        JsonNode response = callPublicApi(buildReferenceUri(properties.getSigunguPath(), "upr_cd", uprCd));
        return extractItems(requireSuccessBody(response)).stream()
                .map(item -> toReference(item, "orgCd", "orgdownNm"))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ShelterPublicApiReference> fetchShelters(String uprCd, String orgCd) {
        requireText(uprCd, "MISSING_UPR_CD", "시도 코드는 필수입니다.");
        requireText(orgCd, "MISSING_ORG_CD", "시군구 코드는 필수입니다.");
        UriComponentsBuilder builder = withCommonQuery(properties.getShelterPath())
                .queryParam("upr_cd", uprCd)
                .queryParam("org_cd", orgCd)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1000);
        JsonNode response = callPublicApi(builder.build(true).toUri());
        return extractItems(requireSuccessBody(response)).stream()
                .map(item -> toReference(item, "careRegNo", "careNm"))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ShelterPublicApiReference> fetchKinds(String upKindCd) {
        requireText(upKindCd, "MISSING_UP_KIND_CD", "축종 코드는 필수입니다.");
        JsonNode response = callPublicApi(buildReferenceUri(properties.getKindPath(), "up_kind_cd", upKindCd));
        return extractItems(requireSuccessBody(response)).stream()
                .map(item -> toReference(item, "kindCd", "kindNm"))
                .filter(Objects::nonNull)
                .toList();
    }

    private JsonNode callAbandonmentApi(URI uri) {
        return callPublicApi(uri);
    }

    private JsonNode callPublicApi(URI uri) {
        validateConfiguration();
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Shelter public API request failed. status={} body={}", response.statusCode(), response.body());
                throw ApiException.internal("SHELTER_API_ERROR", "외부 보호소 API 호출에 실패했습니다.");
            }
            return objectMapper.readTree(response.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call shelter public API", e);
            throw ApiException.internal("SHELTER_API_ERROR", "외부 보호소 API 호출 중 오류가 발생했습니다.");
        }
    }

    private URI buildListUri(String region, String breed, String status, int page, int size) {
        UriComponentsBuilder builder = withCommonQuery(properties.getAbandonmentPath());
        if (StringUtils.hasText(region)) {
            builder.queryParam("upr_cd", region);
        }
        if (StringUtils.hasText(breed)) {
            builder.queryParam("kind", breed);
        }
        if (StringUtils.hasText(status)) {
            builder.queryParam("state", normalizeStateFilter(status));
        }
        builder.queryParam("pageNo", toApiPage(page));
        builder.queryParam("numOfRows", normalizeSize(size));
        return builder.build(true).toUri();
    }

    private URI buildDetailUri(String desertionNo) {
        return withCommonQuery(properties.getAbandonmentPath())
                .queryParam("desertion_no", desertionNo)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1)
                .build(true)
                .toUri();
    }

    private URI buildReferenceUri(String path, String key, String value) {
        UriComponentsBuilder builder = withCommonQuery(path)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1000);
        if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
            builder.queryParam(key, value);
        }
        return builder.build(true).toUri();
    }

    private UriComponentsBuilder withCommonQuery(String path) {
        return UriComponentsBuilder.fromUriString(properties.getBaseUrl() + path)
                .queryParam("serviceKey", properties.getServiceKey())
                .queryParam("_type", "json");
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getServiceKey())) {
            throw ApiException.internal("SHELTER_API_NOT_CONFIGURED", "외부 보호소 API 인증키가 설정되지 않았습니다.");
        }
    }

    private JsonNode requireSuccessBody(JsonNode response) {
        JsonNode payload = response.path("response");
        if (payload.isMissingNode() || payload.isNull()) {
            payload = response;
        }
        JsonNode header = payload.path("header");
        String resultCode = header.path("resultCode").asText("");
        if (!"00".equals(resultCode)) {
            String message = header.path("errorMsg").asText(header.path("resultMsg").asText("외부 보호소 API 응답이 올바르지 않습니다."));
            throw ApiException.internal("SHELTER_API_ERROR", message);
        }
        return payload.path("body");
    }

    private List<JsonNode> extractItems(JsonNode body) {
        JsonNode itemNode = body.path("items").path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();
        }
        if (itemNode.isArray()) {
            List<JsonNode> items = new ArrayList<>();
            itemNode.forEach(items::add);
            return items;
        }
        return List.of(itemNode);
    }

    private ShelterPublicApiAnimal toAnimal(JsonNode item) {
        String id = text(item, "desertionNo");
        if (!StringUtils.hasText(id)) {
            return null;
        }
        return new ShelterPublicApiAnimal(
                id,
                text(item, "noticeNo"),
                text(item, "processState"),
                text(item, "careNm"),
                text(item, "careTel"),
                text(item, "careAddr"),
                text(item, "orgNm"),
                text(item, "kindNm"),
                text(item, "kindFullNm"),
                text(item, "specialMark"),
                text(item, "happenPlace"),
                text(item, "happenDt"),
                text(item, "noticeSdt"),
                text(item, "noticeEdt"),
                text(item, "updTm"),
                collectImages(item)
        );
    }

    private List<String> collectImages(JsonNode item) {
        List<String> images = new ArrayList<>();
        for (int index = 1; index <= 8; index++) {
            String imageUrl = text(item, "popfile" + index);
            if (StringUtils.hasText(imageUrl)) {
                images.add(imageUrl);
            }
        }
        return images;
    }

    private ShelterPublicApiReference toReference(JsonNode item, String codeField, String nameField) {
        String code = text(item, codeField);
        String name = text(item, nameField);
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            return null;
        }
        return new ShelterPublicApiReference(code, name);
    }

    private Comparator<ShelterPublicApiAnimal> resolveComparator(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Comparator.comparing(ShelterPublicApiAnimal::updatedAt, Comparator.nullsLast(String::compareTo)).reversed();
        }
        String normalized = sort.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OLDEST" -> Comparator.comparing(ShelterPublicApiAnimal::noticeStartDate, Comparator.nullsLast(String::compareTo));
            case "LATEST", "NOTICE_EDT_DESC", "UPDATED_DESC" ->
                    Comparator.comparing(ShelterPublicApiAnimal::updatedAt, Comparator.nullsLast(String::compareTo)).reversed();
            default -> Comparator.comparing(ShelterPublicApiAnimal::noticeStartDate, Comparator.nullsLast(String::compareTo)).reversed();
        };
    }

    private String normalizeStateFilter(String status) {
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "open", "notice", "noticed" -> "notice";
            case "protect", "protected", "resolved" -> "protect";
            default -> status;
        };
    }

    private int toApiPage(int page) {
        return page <= 0 ? 1 : page + 1;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 1000);
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw ApiException.badRequest(code, message);
        }
    }

    private String text(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText("");
        return StringUtils.hasText(value) ? value : null;
    }

    public record ShelterPublicApiPage(
            int pageNo,
            int numOfRows,
            int totalCount,
            List<ShelterPublicApiAnimal> items
    ) {
    }

    public record ShelterPublicApiAnimal(
            String desertionNo,
            String noticeNo,
            String processState,
            String careName,
            String careTel,
            String careAddress,
            String organizationName,
            String kindName,
            String kindFullName,
            String specialMark,
            String happenPlace,
            String happenDate,
            String noticeStartDate,
            String noticeEndDate,
            String updatedAt,
            List<String> imageUrls
    ) {
        public String source() {
            return SOURCE;
        }
    }

    public record ShelterPublicApiReference(
            String code,
            String name
    ) {
    }
}
