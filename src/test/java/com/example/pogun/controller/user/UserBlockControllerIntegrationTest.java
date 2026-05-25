package com.example.pogun.controller.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserBlock;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.user.UserBlockRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.support.IntegrationTestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class UserBlockControllerIntegrationTest extends IntegrationTestProperties {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserBlockRepository userBlockRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void blocks_returnsCurrentUsersBlockedList() throws Exception {
        User blocker = userRepository.save(user("blocker-" + UUID.randomUUID(), "blocker"));
        User blocked1 = userRepository.save(user("blocked1-" + UUID.randomUUID(), "blocked-one"));
        User blocked2 = userRepository.save(user("blocked2-" + UUID.randomUUID(), "blocked-two"));
        User otherBlocker = userRepository.save(user("other-" + UUID.randomUUID(), "other"));
        User otherBlocked = userRepository.save(user("other-target-" + UUID.randomUUID(), "other-target"));

        userBlockRepository.save(UserBlock.builder().blocker(blocker).blocked(blocked1).build());
        userBlockRepository.save(UserBlock.builder().blocker(blocker).blocked(blocked2).build());
        userBlockRepository.save(UserBlock.builder().blocker(otherBlocker).blocked(otherBlocked).build());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                blocker.getFirebaseUid(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc.perform(get("/api/users/blocks").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").exists())
                .andExpect(jsonPath("$.data[0].nickname").exists())
                .andExpect(jsonPath("$.data[0].blockedAt").exists());
    }

    private User user(String uidSeed, String nickname) {
        String uid = uidSeed.replace("-", "");
        return User.builder()
                .firebaseUid(uid)
                .email(uid + "@local.test")
                .nickname(nickname)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
