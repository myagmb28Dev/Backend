package com.example.pogun.config.noticechat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class NoticeChatSchemaCompatibilityConfig {

    private final JdbcTemplate jdbcTemplate;
    @Value("${app.schema-sync.enabled:true}")
    private boolean schemaSyncEnabled;

    @Bean
    ApplicationRunner noticeChatMessageTypeCheckConstraintUpdater() {
        return args -> {
            if (!schemaSyncEnabled) {
                log.info("NoticeChat schema sync skipped (app.schema-sync.enabled=false)");
                return;
            }
            try {
                jdbcTemplate.execute("ALTER TABLE notice_chat_messages ALTER COLUMN message DROP NOT NULL");
                jdbcTemplate.execute("ALTER TABLE notice_chat_messages ADD COLUMN IF NOT EXISTS edited_at timestamp with time zone");
                jdbcTemplate.execute("ALTER TABLE notice_chat_messages ADD COLUMN IF NOT EXISTS deleted_at timestamp with time zone");
                jdbcTemplate.execute("ALTER TABLE notice_chat_room_participant_states ADD COLUMN IF NOT EXISTS last_read_room_sequence bigint");
                jdbcTemplate.execute("ALTER TABLE notice_chat_rooms ADD COLUMN IF NOT EXISTS last_message_sequence bigint");
                jdbcTemplate.execute("""
                        UPDATE notice_chat_rooms r
                        SET last_message_sequence = sequence_snapshot.max_room_sequence
                        FROM (
                            SELECT room_id, MAX(room_sequence) AS max_room_sequence
                            FROM notice_chat_messages
                            WHERE room_sequence IS NOT NULL
                            GROUP BY room_id
                        ) sequence_snapshot
                        WHERE r.id = sequence_snapshot.room_id
                          AND (r.last_message_sequence IS NULL OR r.last_message_sequence < sequence_snapshot.max_room_sequence)
                        """);
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_notice_chat_messages_room_deleted_sequence ON notice_chat_messages (room_id, deleted_at, room_sequence)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_notice_chat_rooms_last_message_sequence ON notice_chat_rooms (last_message_sequence)");
                jdbcTemplate.execute("ALTER TABLE notice_chat_messages DROP CONSTRAINT IF EXISTS notice_chat_messages_message_type_check");
                jdbcTemplate.execute("""
                        ALTER TABLE notice_chat_messages
                        ADD CONSTRAINT notice_chat_messages_message_type_check
                        CHECK (message_type IN ('TEXT', 'IMAGE', 'VIDEO'))
                        """);
                jdbcTemplate.execute("ALTER TABLE notice_chat_rooms DROP CONSTRAINT IF EXISTS notice_chat_rooms_last_message_type_check");
                jdbcTemplate.execute("""
                        ALTER TABLE notice_chat_rooms
                        ADD CONSTRAINT notice_chat_rooms_last_message_type_check
                        CHECK (last_message_type IS NULL OR last_message_type IN ('TEXT', 'IMAGE', 'VIDEO'))
                        """);
            } catch (Exception e) {
                log.warn("notice chat message type check constraint update skipped", e);
            }
        };
    }
}
