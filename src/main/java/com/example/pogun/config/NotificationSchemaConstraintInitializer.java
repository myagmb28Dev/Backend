package com.example.pogun.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationSchemaConstraintInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
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

            String constraintDefinition = jdbcTemplate.query(
                    """
                            select pg_get_constraintdef(oid)
                            from pg_constraint
                            where conrelid = 'notifications'::regclass
                              and conname = 'notifications_target_type_check'
                            """,
                    resultSet -> resultSet.next() ? resultSet.getString(1) : ""
            );

            if (constraintDefinition != null && constraintDefinition.contains("ADMIN_BROADCAST")) {
                return;
            }

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
        } catch (RuntimeException e) {
            log.warn("Failed to synchronize notifications_target_type_check constraint", e);
        }
    }
}
