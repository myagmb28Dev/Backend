package com.example.pogun.service.auth;

import com.example.pogun.entity.user.PendingSocialSignup;
import com.example.pogun.repository.user.PendingSocialSignupRepository;
import com.example.pogun.support.IntegrationTestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PendingSocialSignupCleanupScheduler.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pending_social_signup_cleanup_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON"
})
class PendingSocialSignupCleanupSchedulerTest extends IntegrationTestProperties {

    @Autowired
    private PendingSocialSignupCleanupScheduler cleanupScheduler;

    @Autowired
    private PendingSocialSignupRepository pendingSocialSignupRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void purgeExpiredPendingSignups_runsDeleteInsideTransaction() {
        Instant now = Instant.now();
        pendingSocialSignupRepository.saveAndFlush(pendingSignup("expired-uid", "expired@example.com", now.minusSeconds(1)));
        pendingSocialSignupRepository.saveAndFlush(pendingSignup("active-uid", "active@example.com", now.plusSeconds(3600)));

        cleanupScheduler.purgeExpiredPendingSignups();

        assertThat(pendingSocialSignupRepository.findByFirebaseUid("expired-uid")).isEmpty();
        assertThat(pendingSocialSignupRepository.findByFirebaseUid("active-uid")).isPresent();
    }

    private PendingSocialSignup pendingSignup(String firebaseUid, String email, Instant expiresAt) {
        return PendingSocialSignup.builder()
                .firebaseUid(firebaseUid)
                .email(email)
                .nickname("pending-user")
                .provider("GOOGLE")
                .linkedProviders("GOOGLE")
                .expiresAt(expiresAt)
                .build();
    }
}
