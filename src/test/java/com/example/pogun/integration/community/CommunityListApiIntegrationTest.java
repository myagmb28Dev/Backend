package com.example.pogun.integration.community;

import com.example.pogun.controller.community.CommunityController;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.CommunityPostImage;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.community.CommunityImageStorageService;
import com.example.pogun.service.community.CommunityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityListApiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.datasource.url=jdbc:h2:mem:communityapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.show-sql=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration"
        }
)
class CommunityListApiIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        communityPostRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("커뮤니티 목록 API 시나리오: 최신/인기/페이징/썸네일/숨김삭제제외")
    void communityListApiScenario() throws Exception {
        User author = userRepository.save(User.builder()
                .firebaseUid("integration-uid-1")
                .email("integration-user@example.com")
                .nickname("통합테스터")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        savePost(author, "active-old", CommunityPostStatus.ACTIVE, 10L, 1L, List.of());
        Thread.sleep(5L);
        savePost(author, "active-popular", CommunityPostStatus.ACTIVE, 200L, 2L, List.of("https://example.com/popular-thumb.jpg"));
        Thread.sleep(5L);
        savePost(author, "active-latest", CommunityPostStatus.ACTIVE, 50L, 5L, List.of("https://example.com/latest-thumb.jpg", "https://example.com/latest-second.jpg"));
        savePost(author, "hidden-top", CommunityPostStatus.HIDDEN, 999L, 999L, List.of("https://example.com/hidden.jpg"));
        savePost(author, "deleted-top", CommunityPostStatus.DELETED, 998L, 998L, List.of("https://example.com/deleted.jpg"));

        HttpResponse<String> latestResponse = get("/api/community/posts?type=LATEST&page=0&size=20");
        assertThat(latestResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode latestRoot = objectMapper.readTree(latestResponse.body());
        JsonNode latestItems = latestRoot.path("data").path("items");
        assertThat(latestItems).hasSize(3);
        assertThat(latestRoot.path("data").path("totalElements").asLong()).isEqualTo(3L);
        assertThat(latestRoot.path("data").path("totalPages").asInt()).isEqualTo(1);
        assertThat(latestItems.get(0).path("title").asText()).isEqualTo("active-latest");
        assertThat(latestItems.get(0).path("thumbnailImageUrl").asText()).isEqualTo("https://example.com/latest-thumb.jpg");
        assertThat(extractTitles(latestItems)).doesNotContain("hidden-top", "deleted-top");

        HttpResponse<String> popularResponse = get("/api/community/posts?type=POPULAR&page=0&size=20");
        assertThat(popularResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode popularRoot = objectMapper.readTree(popularResponse.body());
        JsonNode popularItems = popularRoot.path("data").path("items");
        assertThat(popularItems).hasSize(3);
        assertThat(popularItems.get(0).path("title").asText()).isEqualTo("active-popular");
        assertThat(extractTitles(popularItems)).doesNotContain("hidden-top", "deleted-top");

        HttpResponse<String> page0Response = get("/api/community/posts?type=LATEST&page=0&size=2");
        HttpResponse<String> page1Response = get("/api/community/posts?type=LATEST&page=1&size=2");
        assertThat(page0Response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(page1Response.statusCode()).isEqualTo(HttpStatus.OK.value());

        JsonNode page0Root = objectMapper.readTree(page0Response.body());
        JsonNode page1Root = objectMapper.readTree(page1Response.body());
        JsonNode page0Items = page0Root.path("data").path("items");
        JsonNode page1Items = page1Root.path("data").path("items");

        assertThat(page0Root.path("data").path("totalElements").asLong()).isEqualTo(3L);
        assertThat(page0Root.path("data").path("totalPages").asInt()).isEqualTo(2);
        assertThat(page0Items).hasSize(2);
        assertThat(page1Items).hasSize(1);
        assertThat(extractIds(page0Items)).doesNotContainAnyElementsOf(extractIds(page1Items));
    }

    private CommunityPost savePost(User author, String title, CommunityPostStatus status, long viewCount, long likeCount, List<String> imageUrls) {
        CommunityPost post = CommunityPost.builder()
                .author(author)
                .title(title)
                .content("테스트 본문 - " + title)
                .category("FREE")
                .status(status)
                .viewCount(viewCount)
                .likeCount(likeCount)
                .build();
        for (int i = 0; i < imageUrls.size(); i++) {
            post.getImages().add(CommunityPostImage.builder()
                    .post(post)
                    .imageUrl(imageUrls.get(i))
                    .sortOrder(i)
                    .build());
        }
        return communityPostRepository.save(post);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private List<String> extractTitles(JsonNode items) {
        List<String> titles = new ArrayList<>();
        for (JsonNode item : items) {
            titles.add(item.path("title").asText());
        }
        return titles;
    }

    private List<String> extractIds(JsonNode items) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : items) {
            ids.add(item.path("id").asText());
        }
        return ids;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "com.example.pogun")
    @EnableJpaRepositories(basePackages = "com.example.pogun.repository")
    @Import({
            CommunityController.class,
            CommunityService.class,
            TestBeans.class
    })
    static class TestApplication {
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        @Bean
        CommunityImageStorageService communityImageStorageService() {
            return Mockito.mock(CommunityImageStorageService.class);
        }
    }
}















