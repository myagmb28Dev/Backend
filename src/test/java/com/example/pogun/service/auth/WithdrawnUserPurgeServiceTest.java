package com.example.pogun.service.auth;

import com.example.pogun.entity.community.enums.CommunityCommentStatus;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.entity.report.enums.ReportStatus;
import com.example.pogun.entity.report.enums.ReportTargetType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.support.IntegrationTestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:withdrawn_user_purge_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON"
})
class WithdrawnUserPurgeServiceTest extends IntegrationTestProperties {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private WithdrawnUserPurgeService withdrawnUserPurgeService;

    @BeforeEach
    void setUp() {
        withdrawnUserPurgeService = new WithdrawnUserPurgeService(userRepository, jdbcTemplate);
    }

    @Test
    void purgeExpiredWithdrawnAccounts_deletesAccountRecordsAndAnonymizesIdentity() {
        Instant now = Instant.parse("2026-06-30T00:00:00Z");
        User user = saveWithdrawnUser("old-uid", "gone@example.com", now.minus(Duration.ofDays(4)));
        User otherUser = saveActiveUser("other-uid", "other@example.com");
        UUID postId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID commentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        insertCommunityPost(postId, user.getId(), now);
        insertCommunityComment(commentId, postId, user.getId(), now);
        insertReport(reportId, user.getId(), ReportTargetType.COMMUNITY_POST, postId, now);
        insertReport(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                otherUser.getId(),
                ReportTargetType.USER,
                user.getId(),
                now
        );

        int purged = withdrawnUserPurgeService.purgeExpiredWithdrawnAccounts(now.minus(Duration.ofDays(3)));

        assertThat(purged).isEqualTo(1);
        assertThat(countRows("select count(*) from community_posts where author_id = ?", user.getId())).isZero();
        assertThat(countRows("select count(*) from community_comments where author_id = ?", user.getId())).isZero();
        assertThat(countRows("select count(*) from reports where reporter_id = ?", user.getId())).isZero();
        assertThat(countRows("select count(*) from reports where target_type = 'USER' and target_id = ?", user.getId())).isZero();
        assertThat(userRepository.findByEmail("gone@example.com")).isEmpty();
        assertThat(userRepository.findByFirebaseUid("old-uid")).isEmpty();

        User purgedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(purgedUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(purgedUser.getWithdrawnAt()).isNull();
        assertThat(purgedUser.getEmail()).startsWith("withdrawn+");
        assertThat(purgedUser.getFirebaseUid()).startsWith("withdrawn:");
        assertThat(purgedUser.getNickname()).isEqualTo("withdrawn-user");
    }

    @Test
    void purgeExpiredWithdrawnAccounts_keepsWithdrawalRecordsBeforeRetentionEnds() {
        Instant now = Instant.parse("2026-06-30T00:00:00Z");
        User user = saveWithdrawnUser("recent-uid", "recent@example.com", now.minus(Duration.ofDays(2)));
        UUID postId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        insertCommunityPost(postId, user.getId(), now);

        int purged = withdrawnUserPurgeService.purgeExpiredWithdrawnAccounts(now.minus(Duration.ofDays(3)));

        assertThat(purged).isZero();
        assertThat(userRepository.findByEmail("recent@example.com")).isPresent();
        assertThat(userRepository.findByFirebaseUid("recent-uid")).isPresent();
        assertThat(countRows("select count(*) from community_posts where author_id = ?", user.getId())).isEqualTo(1);
    }

    @Test
    void purgeExpiredWithdrawalForEmail_runsCleanupBeforeRejoinLookup() {
        Instant now = Instant.parse("2026-06-30T00:00:00Z");
        User user = saveWithdrawnUser("rejoin-uid", "rejoin@example.com", now.minus(Duration.ofDays(3)));

        boolean purged = withdrawnUserPurgeService.purgeExpiredWithdrawalForEmail("rejoin@example.com", now);

        assertThat(purged).isTrue();
        assertThat(userRepository.findByEmail("rejoin@example.com")).isEmpty();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).startsWith("withdrawn+");
    }

    private User saveWithdrawnUser(String firebaseUid, String email, Instant withdrawnAt) {
        return userRepository.saveAndFlush(User.builder()
                .firebaseUid(firebaseUid)
                .email(email)
                .nickname("old-user")
                .role(UserRole.USER)
                .status(UserStatus.WITHDRAWN)
                .withdrawnAt(withdrawnAt)
                .build());
    }

    private User saveActiveUser(String firebaseUid, String email) {
        return userRepository.saveAndFlush(User.builder()
                .firebaseUid(firebaseUid)
                .email(email)
                .nickname("active-user")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private void insertCommunityPost(UUID postId, UUID authorId, Instant createdAt) {
        jdbcTemplate.update("""
                insert into community_posts
                    (id, author_id, title, content, category, view_count, like_count, status,
                     poll_is_multiple_choice, created_at, updated_at)
                values (?, ?, 'title', 'content', 'FREE', 0, 0, ?, false, ?, ?)
                """, postId, authorId, CommunityPostStatus.ACTIVE.name(), createdAt, createdAt);
    }

    private void insertCommunityComment(UUID commentId, UUID postId, UUID authorId, Instant createdAt) {
        jdbcTemplate.update("""
                insert into community_comments
                    (id, post_id, author_id, content, status, created_at, updated_at)
                values (?, ?, ?, 'comment', ?, ?, ?)
                """, commentId, postId, authorId, CommunityCommentStatus.NORMAL.name(), createdAt, createdAt);
    }

    private void insertReport(UUID reportId, UUID reporterId, ReportTargetType targetType, UUID targetId, Instant createdAt) {
        jdbcTemplate.update("""
                insert into reports
                    (id, reporter_id, target_type, target_id, reason, status, created_at)
                values (?, ?, ?, ?, 'SPAM', ?, ?)
                """, reportId, reporterId, targetType.name(), targetId, ReportStatus.RECEIVED.name(), createdAt);
    }

    private int countRows(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
