package com.example.pogun.service.auth;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawnUserPurgeService {

    static final Duration RETENTION_PERIOD = Duration.ofDays(3);

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${app.auth.withdrawn-user-purge-cron:0 0 4 * * *}")
    @Transactional
    public void purgeExpiredWithdrawnAccounts() {
        int purged = purgeExpiredWithdrawnAccounts(Instant.now().minus(RETENTION_PERIOD));
        if (purged > 0) {
            log.info("expired withdrawn users purged. purged={}", purged);
        }
    }

    @Transactional
    public int purgeExpiredWithdrawnAccounts(Instant cutoff) {
        List<User> expiredUsers = userRepository.findByStatusAndWithdrawnAtLessThanEqual(UserStatus.WITHDRAWN, cutoff);
        expiredUsers.forEach(this::purgeWithdrawnUser);
        return expiredUsers.size();
    }

    @Transactional
    public boolean purgeExpiredWithdrawalForEmail(String email) {
        return purgeExpiredWithdrawalForEmail(email, Instant.now());
    }

    @Transactional
    boolean purgeExpiredWithdrawalForEmail(String email, Instant now) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Instant cutoff = now.minus(RETENTION_PERIOD);
        return userRepository.findByEmail(email)
                .filter(user -> isExpiredWithdrawnUser(user, cutoff))
                .map(user -> {
                    purgeWithdrawnUser(user);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean purgeExpiredWithdrawalForFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return false;
        }
        Instant cutoff = Instant.now().minus(RETENTION_PERIOD);
        return userRepository.findByFirebaseUid(firebaseUid)
                .filter(user -> isExpiredWithdrawnUser(user, cutoff))
                .map(user -> {
                    purgeWithdrawnUser(user);
                    return true;
                })
                .orElse(false);
    }

    private boolean isExpiredWithdrawnUser(User user, Instant cutoff) {
        return user != null
                && user.getStatus() == UserStatus.WITHDRAWN
                && user.getWithdrawnAt() != null
                && !user.getWithdrawnAt().isAfter(cutoff);
    }

    private void purgeWithdrawnUser(User user) {
        UUID userId = user.getId();
        if (userId == null) {
            return;
        }

        deleteNotifications(userId);
        deleteReports(userId);
        deleteNoticeChats(userId);
        deleteMissingPetRecords(userId);
        deleteCommunityRecords(userId);
        deleteDirectUserRecords(userId);
        detachAuditReferences(userId);
        anonymizeUser(user);
        userRepository.save(user);
    }

    private void deleteNotifications(UUID userId) {
        update("""
                delete from notifications
                where user_id = ?
                   or (target_type = 'USER' and target_id = ?)
                   or (target_type = 'COMMUNITY_POST'
                       and target_id in (select p.id from community_posts p where p.author_id = ?))
                   or (target_type = 'COMMUNITY_COMMENT'
                       and target_id in (
                           select c.id from community_comments c
                           where c.author_id = ?
                              or c.post_id in (select p.id from community_posts p where p.author_id = ?)
                       ))
                   or (target_type = 'PET_NOTICE'
                       and target_id in (select n.id from pet_notices n where n.author_id = ?))
                   or (target_type = 'NOTICE_CHAT_ROOM'
                       and target_id in (
                           select r.id from notice_chat_rooms r
                           where r.owner_user_id = ?
                              or r.guest_user_id = ?
                              or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                       ))
                   or (target_type = 'NOTICE_CHAT_MESSAGE'
                       and target_id in (
                           select m.id from notice_chat_messages m
                           where m.sender_user_id = ?
                              or m.room_id in (
                                  select r.id from notice_chat_rooms r
                                  where r.owner_user_id = ?
                                     or r.guest_user_id = ?
                                     or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                              )
                       ))
                """,
                userId, userId, userId, userId, userId, userId, userId, userId, userId, userId, userId, userId, userId);
        update("update notifications set actor_user_id = null where actor_user_id = ?", userId);
    }

    private void deleteReports(UUID userId) {
        update("""
                delete from reports
                where reporter_id = ?
                   or (target_type = 'USER' and target_id = ?)
                   or (target_type = 'COMMUNITY_POST'
                       and target_id in (select p.id from community_posts p where p.author_id = ?))
                   or (target_type = 'COMMUNITY_COMMENT'
                       and target_id in (
                           select c.id from community_comments c
                           where c.author_id = ?
                              or c.post_id in (select p.id from community_posts p where p.author_id = ?)
                       ))
                   or (target_type = 'PET_NOTICE'
                       and target_id in (select n.id from pet_notices n where n.author_id = ?))
                   or (target_type = 'NOTICE_CHAT_ROOM'
                       and target_id in (
                           select r.id from notice_chat_rooms r
                           where r.owner_user_id = ?
                              or r.guest_user_id = ?
                              or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                       ))
                """,
                userId, userId, userId, userId, userId, userId, userId, userId, userId);
        update("update reports set reviewed_by_id = null where reviewed_by_id = ?", userId);
    }

    private void deleteNoticeChats(UUID userId) {
        update("""
                update notice_chat_read_receipts
                set last_read_message_id = null
                where last_read_message_id in (
                    select m.id from notice_chat_messages m
                    where m.sender_user_id = ?
                       or m.room_id in (
                           select r.id from notice_chat_rooms r
                           where r.owner_user_id = ?
                              or r.guest_user_id = ?
                              or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                       )
                )
                """, userId, userId, userId, userId);
        update("""
                update notice_chat_room_participant_states
                set last_read_message_id = null
                where last_read_message_id in (
                    select m.id from notice_chat_messages m
                    where m.sender_user_id = ?
                       or m.room_id in (
                           select r.id from notice_chat_rooms r
                           where r.owner_user_id = ?
                              or r.guest_user_id = ?
                              or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                       )
                )
                """, userId, userId, userId, userId);
        update("""
                update notice_chat_messages
                set reply_to_message_id = null
                where reply_to_message_id in (
                    select m.id from notice_chat_messages m
                    where m.sender_user_id = ?
                       or m.room_id in (
                           select r.id from notice_chat_rooms r
                           where r.owner_user_id = ?
                              or r.guest_user_id = ?
                              or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                       )
                )
                """, userId, userId, userId, userId);
        update("""
                delete from notice_chat_read_receipts
                where reader_user_id = ?
                   or room_id in (
                       select r.id from notice_chat_rooms r
                       where r.owner_user_id = ?
                          or r.guest_user_id = ?
                          or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                   )
                """, userId, userId, userId, userId);
        update("""
                delete from notice_chat_room_participant_states
                where user_id = ?
                   or room_id in (
                       select r.id from notice_chat_rooms r
                       where r.owner_user_id = ?
                          or r.guest_user_id = ?
                          or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                   )
                """, userId, userId, userId, userId);
        update("""
                delete from notice_chat_message_images
                where message_id in (
                    select m.id from notice_chat_messages m
                    where m.sender_user_id = ?
                       or m.room_id in (
                           select r.id from notice_chat_rooms r
                           where r.owner_user_id = ?
                              or r.guest_user_id = ?
                              or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                       )
                )
                """, userId, userId, userId, userId);
        update("""
                delete from notice_chat_messages
                where sender_user_id = ?
                   or room_id in (
                       select r.id from notice_chat_rooms r
                       where r.owner_user_id = ?
                          or r.guest_user_id = ?
                          or r.notice_id in (select n.id from pet_notices n where n.author_id = ?)
                   )
                """, userId, userId, userId, userId);
        update("""
                delete from notice_chat_rooms
                where owner_user_id = ?
                   or guest_user_id = ?
                   or notice_id in (select n.id from pet_notices n where n.author_id = ?)
                """, userId, userId, userId);
    }

    private void deleteMissingPetRecords(UUID userId) {
        update("""
                delete from notice_bookmarks
                where user_id = ?
                   or notice_id in (select n.id from pet_notices n where n.author_id = ?)
                """, userId, userId);
        update("""
                delete from pet_notice_images
                where notice_id in (select n.id from pet_notices n where n.author_id = ?)
                """, userId);
        update("delete from pet_notices where author_id = ?", userId);
    }

    private void deleteCommunityRecords(UUID userId) {
        update("""
                update community_comments
                set parent_comment_id = null
                where parent_comment_id in (
                    select c.id from community_comments c
                    where c.author_id = ?
                       or c.post_id in (select p.id from community_posts p where p.author_id = ?)
                )
                """, userId, userId);
        update("""
                delete from community_post_votes
                where user_id = ?
                   or post_id in (select p.id from community_posts p where p.author_id = ?)
                """, userId, userId);
        update("""
                delete from community_post_reactions
                where user_id = ?
                   or post_id in (select p.id from community_posts p where p.author_id = ?)
                """, userId, userId);
        update("""
                delete from community_comments
                where author_id = ?
                   or post_id in (select p.id from community_posts p where p.author_id = ?)
                """, userId, userId);
        update("""
                delete from community_post_tags
                where post_id in (select p.id from community_posts p where p.author_id = ?)
                """, userId);
        update("""
                delete from community_post_images
                where post_id in (select p.id from community_posts p where p.author_id = ?)
                """, userId);
        update("delete from community_posts where author_id = ?", userId);
    }

    private void deleteDirectUserRecords(UUID userId) {
        update("delete from user_credit_balances where user_id = ?", userId);
        update("delete from ai_analyses where author_id = ?", userId);
        update("delete from user_fcm_tokens where user_id = ?", userId);
        update("delete from user_notification_settings where user_id = ?", userId);
        update("delete from user_social_accounts where user_id = ?", userId);
        update("delete from user_follows where follower_id = ? or following_id = ?", userId, userId);
        update("delete from user_blocks where blocker_id = ? or blocked_id = ?", userId, userId);
        update("delete from admin_auth_challenges where user_id = ?", userId);
        update("delete from admin_sessions where user_id = ?", userId);
        update("delete from admin_passkeys where user_id = ?", userId);
        update("delete from admin_permission_assignments where user_id = ?", userId);
    }

    private void detachAuditReferences(UUID userId) {
        update("update admin_settings set updated_by_user_id = null where updated_by_user_id = ?", userId);
        update("update admin_reference_data set updated_by_user_id = null where updated_by_user_id = ?", userId);
        update("update admin_notification_dispatches set actor_user_id = null where actor_user_id = ?", userId);
        update("update admin_audit_logs set actor_user_id = null where actor_user_id = ?", userId);
    }

    private void anonymizeUser(User user) {
        String userId = user.getId().toString();
        user.setFirebaseUid("withdrawn:" + userId);
        user.setEmail("withdrawn+" + userId + "@deleted.local");
        user.setNickname("withdrawn-user");
        user.setProfileImageUrl(null);
        user.setPhoneNumber(null);
        user.setRegion(null);
        user.setRegionType(null);
        user.setRegionAddressName(null);
        user.setRegion1DepthName(null);
        user.setRegion2DepthName(null);
        user.setRegion3DepthName(null);
        user.setAuthProvider(null);
        user.setLastActiveAt(null);
        user.setWithdrawnAt(null);
        user.setAvailabilityStatus(UserAvailabilityStatus.OFFLINE);
    }

    private int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }
}
