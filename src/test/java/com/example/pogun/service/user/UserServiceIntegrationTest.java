package com.example.pogun.service.user;

import com.example.pogun.dto.user.UserPetNoticeSummaryResponse;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.PetNoticeImage;
import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.support.IntegrationTestProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserServiceIntegrationTest extends IntegrationTestProperties {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PetNoticeRepository petNoticeRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void myPetNotices_returnsImageUrlsWithOpenInViewDisabled() {
        User user = userRepository.save(User.builder()
                .firebaseUid("my-posts-" + UUID.randomUUID())
                .email("my-posts-" + UUID.randomUUID() + "@local.test")
                .nickname("my-posts-user")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        PetNotice notice = PetNotice.builder()
                .author(user)
                .title("Missing pet")
                .animalType("DOG")
                .breed("Poodle")
                .gender(PetGender.UNKNOWN)
                .missingDate(Instant.parse("2026-06-08T00:00:00Z"))
                .missingRegion("Seoul")
                .status(PetNoticeStatus.OPEN)
                .viewCount(12L)
                .images(List.of(
                        PetNoticeImage.builder().imageUrl("https://cdn.example.com/pet-1.jpg").sortOrder(0).build()
                ))
                .build();
        notice.getImages().forEach(image -> image.setNotice(notice));
        petNoticeRepository.saveAndFlush(notice);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getFirebaseUid(), "N/A")
        );

        List<UserPetNoticeSummaryResponse> response = userService.myPetNotices();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).noticeId()).isEqualTo(notice.getId());
        assertThat(response.get(0).imageUrls()).containsExactly("https://cdn.example.com/pet-1.jpg");
    }
}
