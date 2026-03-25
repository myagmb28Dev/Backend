package com.example.pogun;

import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.community.CommunityPostStatus;
import com.example.pogun.entity.missingpet.PetGender;
import com.example.pogun.entity.missingpet.PetNoticeStatus;
import com.example.pogun.entity.user.UserRole;
import com.example.pogun.entity.user.UserStatus;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SqlInjectionRepositoryTest.TestApplication.class, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:sqlinjtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
@Transactional
class SqlInjectionRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PetNoticeRepository petNoticeRepository;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Test
    @DisplayName("로그인 식별자 조회는 SQL injection 문자열을 일반 값으로 처리한다")
    void findByFirebaseUid_treatsSqlInjectionAsLiteral() {
        userRepository.save(User.builder()
                .firebaseUid("google-uid-123")
                .email("user@example.com")
                .nickname("테스트유저")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        assertThat(userRepository.findByFirebaseUid("google-uid-123")).isPresent();
        assertThat(userRepository.findByFirebaseUid("' OR 1=1 --")).isEmpty();
        assertThat(userRepository.findByFirebaseUid("' OR firebase_uid IS NOT NULL --")).isEmpty();
    }

    @Test
    @DisplayName("실종 공고 필터 쿼리는 SQL injection 문자열로 전체 조회되지 않는다")
    void findNotices_doesNotExpandResultsForSqlInjectionInput() {
        User author = userRepository.save(User.builder()
                .firebaseUid("author-uid-1")
                .email("author@example.com")
                .nickname("작성자")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        petNoticeRepository.save(PetNotice.builder()
                .author(author)
                .title("말티즈를 찾습니다")
                .animalType("DOG")
                .breed("말티즈")
                .gender(PetGender.MALE)
                .missingDate(Instant.parse("2026-03-20T10:00:00Z"))
                .missingRegion("서울")
                .status(PetNoticeStatus.OPEN)
                .viewCount(0L)
                .build());

        assertThat(petNoticeRepository.findNotices("서울", "말티즈", PetNoticeStatus.OPEN, null, null)).hasSize(1);
        assertThat(petNoticeRepository.findNotices("' OR 1=1 --", null, null, null, null)).isEmpty();
        assertThat(petNoticeRepository.findNotices("서울' OR '1'='1", null, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("커뮤니티 검색 쿼리는 SQL injection 문자열로 전체 조회되지 않는다")
    void findPosts_doesNotExpandResultsForSqlInjectionInput() {
        User author = userRepository.save(User.builder()
                .firebaseUid("author-uid-2")
                .email("community@example.com")
                .nickname("커뮤니티작성자")
                .authProvider("GOOGLE")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        communityPostRepository.save(CommunityPost.builder()
                .author(author)
                .title("강아지 산책 친구 구해요")
                .content("주말에 같이 산책하실 분")
                .status(CommunityPostStatus.ACTIVE)
                .viewCount(3L)
                .build());

        Page<CommunityPost> normalResult = communityPostRepository.findPosts(
                CommunityPostStatus.ACTIVE,
                null,
                null,
                "강아지",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Page<CommunityPost> injectionResult = communityPostRepository.findPosts(
                CommunityPostStatus.ACTIVE,
                null,
                null,
                "' OR 1=1 --",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(normalResult.getTotalElements()).isEqualTo(1);
        assertThat(injectionResult.getContent()).isEmpty();
        assertThat(injectionResult.getTotalElements()).isZero();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
