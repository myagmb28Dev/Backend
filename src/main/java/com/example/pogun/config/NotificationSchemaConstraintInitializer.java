package com.example.pogun.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationSchemaConstraintInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    @Value("${app.schema-sync.enabled:true}")
    private boolean schemaSyncEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!schemaSyncEnabled) {
            log.info("Notification schema sync skipped (app.schema-sync.enabled=false)");
            return;
        }
        try {
            Boolean notificationsTableExists = jdbcTemplate.queryForObject(
                    """
                            select exists (
                                select 1
                                from information_schema.tables
                                where table_schema = 'public'
                                  and table_name = 'notifications'
                            )
                            """,
                    Boolean.class
            );
            if (!Boolean.TRUE.equals(notificationsTableExists)) {
                return;
            }

            String targetTypeConstraintDefinition = jdbcTemplate.query(
                    """
                            select pg_get_constraintdef(oid)
                            from pg_constraint
                            where conrelid = 'notifications'::regclass
                              and conname = 'notifications_target_type_check'
                    """,
                    resultSet -> resultSet.next() ? resultSet.getString(1) : ""
            );

            if (targetTypeConstraintDefinition == null || !targetTypeConstraintDefinition.contains("ADMIN_BROADCAST")) {
                jdbcTemplate.execute("alter table notifications drop constraint if exists notifications_target_type_check");
                jdbcTemplate.execute(
                        """
                                alter table notifications
                                add constraint notifications_target_type_check
                                check (target_type in (
                                    'PET_NOTICE',
                                    'NOTICE_CHAT_ROOM',
                                    'NOTICE_CHAT_MESSAGE',
                                    'COMMUNITY_POST',
                                    'COMMUNITY_COMMENT',
                                    'REPORT',
                                    'USER',
                                    'ADMIN_BROADCAST'
                                ))
                                """
                );
                log.info("notifications_target_type_check constraint synchronized with NotificationTargetType enum values");
            }

            String typeConstraintDefinition = jdbcTemplate.query(
                    """
                            select pg_get_constraintdef(oid)
                            from pg_constraint
                            where conrelid = 'notifications'::regclass
                              and conname = 'notifications_type_check'
                            """,
                    resultSet -> resultSet.next() ? resultSet.getString(1) : ""
            );

            if (typeConstraintDefinition == null
                    || !typeConstraintDefinition.contains("ADMIN_BROADCAST")
                    || !typeConstraintDefinition.contains("AI_SIMILAR_NOTICE_FOUND")) {
                jdbcTemplate.execute("alter table notifications drop constraint if exists notifications_type_check");
                jdbcTemplate.execute(
                        """
                                alter table notifications
                                add constraint notifications_type_check
                                check (type in (
                                    'NEW_NOTICE',
                                    'AI_SIMILAR_NOTICE_FOUND',
                                    'NOTICE_COMMENT',
                                    'NOTICE_STATUS_CHANGED',
                                    'COMMUNITY_COMMENT',
                                    'DM_MESSAGE',
                                    'DM_REPLY',
                                    'COMMUNITY_POST_LIKE',
                                    'COMMUNITY_POST_COMMENT',
                                    'COMMUNITY_COMMENT_REPLY',
                                    'REPORT_RESULT',
                                    'FOLLOWED_ME',
                                    'FOLLOWING_POST',
                                    'COMMUNITY_NOTICE',
                                    'CHAT_ROOM_NOTICE',
                                    'ADMIN_BROADCAST'
                                ))
                                """
                );
                log.info("notifications_type_check constraint synchronized with NotificationType enum values");
            }

            Boolean settingsTableExists = jdbcTemplate.queryForObject(
                    """
                            select exists (
                                select 1
                                from information_schema.tables
                                where table_schema = 'public'
                                  and table_name = 'user_notification_settings'
                            )
                            """,
                    Boolean.class
            );
            if (!Boolean.TRUE.equals(settingsTableExists)) {
                return;
            }

            String settingsTypeConstraintDefinition = jdbcTemplate.query(
                    """
                            select pg_get_constraintdef(oid)
                            from pg_constraint
                            where conrelid = 'user_notification_settings'::regclass
                              and conname = 'user_notification_settings_type_check'
                            """,
                    resultSet -> resultSet.next() ? resultSet.getString(1) : ""
            );

            if (settingsTypeConstraintDefinition == null
                    || !settingsTypeConstraintDefinition.contains("ADMIN_BROADCAST")
                    || !settingsTypeConstraintDefinition.contains("AI_SIMILAR_NOTICE_FOUND")) {
                jdbcTemplate.execute("alter table user_notification_settings drop constraint if exists user_notification_settings_type_check");
                jdbcTemplate.execute(
                        """
                                alter table user_notification_settings
                                add constraint user_notification_settings_type_check
                                check (type in (
                                    'NEW_NOTICE',
                                    'AI_SIMILAR_NOTICE_FOUND',
                                    'NOTICE_COMMENT',
                                    'NOTICE_STATUS_CHANGED',
                                    'COMMUNITY_COMMENT',
                                    'DM_MESSAGE',
                                    'DM_REPLY',
                                    'COMMUNITY_POST_LIKE',
                                    'COMMUNITY_POST_COMMENT',
                                    'COMMUNITY_COMMENT_REPLY',
                                    'REPORT_RESULT',
                                    'FOLLOWED_ME',
                                    'FOLLOWING_POST',
                                    'COMMUNITY_NOTICE',
                                    'CHAT_ROOM_NOTICE',
                                    'ADMIN_BROADCAST'
                                ))
                                """
                );
                log.info("user_notification_settings_type_check constraint synchronized with NotificationType enum values");
            }
        } catch (RuntimeException e) {
            log.warn("Failed to synchronize notifications check constraints", e);
        }
    }
}
