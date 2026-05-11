package com.example.pogun.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class JdbcDeleteUsersByEmail {

    private record FkRef(String schema, String table, String column) {
    }

    public static void main(String[] args) throws Exception {
        List<String> emails = normalizeEmails(args);
        if (emails.isEmpty()) {
            throw new IllegalArgumentException("Usage: JdbcDeleteUsersByEmail <email1> <email2> ...");
        }

        Map<String, String> envFile = loadEnv(Paths.get(".env"));
        String dbUrl = firstNonBlank(System.getenv("DB_URL"), envFile.get("DB_URL"));
        String dbUser = firstNonBlank(System.getenv("DB_USERNAME"), envFile.get("DB_USERNAME"));
        String dbPass = firstNonBlank(System.getenv("DB_PASSWORD"), envFile.get("DB_PASSWORD"));

        if (isBlank(dbUrl) || isBlank(dbUser) || isBlank(dbPass)) {
            throw new IllegalStateException("Missing DB_URL/DB_USERNAME/DB_PASSWORD (env or .env)");
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            conn.setAutoCommit(false);
            try {
                Map<String, UUID> targetUsers = resolveUserIds(conn, emails);
                if (targetUsers.isEmpty()) {
                    System.out.println("No matching users found. Nothing deleted.");
                    conn.rollback();
                    return;
                }

                List<UUID> userIds = new ArrayList<>(targetUsers.values());
                System.out.println("[targets]");
                targetUsers.forEach((email, id) -> System.out.println(email + " -> " + id));

                long preDeletedRows = executeOrderedCleanup(conn, userIds);

                long deletedFkRows = 0;
                for (FkRef ref : loadUserFkRefs(conn)) {
                    long deleted = deleteByFk(conn, ref, userIds);
                    if (deleted > 0) {
                        deletedFkRows += deleted;
                        System.out.println("Deleted " + deleted + " from " + q(ref.schema) + "." + q(ref.table) + " by " + q(ref.column));
                    }
                }

                long deletedUsers = deleteUsers(conn, userIds);
                conn.commit();

                System.out.println("[summary]");
                System.out.println("Deleted users: " + deletedUsers);
                System.out.println("Deleted ordered rows: " + preDeletedRows);
                System.out.println("Deleted FK rows: " + deletedFkRows);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static Map<String, UUID> resolveUserIds(Connection conn, List<String> emails) throws Exception {
        String sql = "select id, email from users where lower(email) in (" + placeholders(emails.size()) + ")";
        Map<String, UUID> targets = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < emails.size(); i++) {
                ps.setString(i + 1, emails.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object raw = rs.getObject("id");
                    UUID id = (raw instanceof UUID u) ? u : UUID.fromString(String.valueOf(raw));
                    targets.put(rs.getString("email"), id);
                }
            }
        }
        return targets;
    }

    private static List<FkRef> loadUserFkRefs(Connection conn) throws Exception {
        String sql = """
                select
                  tc.table_schema,
                  tc.table_name,
                  kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name
                 and tc.table_schema = kcu.table_schema
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name
                 and ccu.table_schema = tc.table_schema
                where tc.constraint_type = 'FOREIGN KEY'
                  and ccu.table_name = 'users'
                  and ccu.column_name = 'id'
                order by tc.table_schema, tc.table_name, kcu.ordinal_position
                """;

        List<FkRef> refs = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String schema = rs.getString(1);
                String table = rs.getString(2);
                String column = rs.getString(3);
                if (schema == null || table == null || column == null) {
                    continue;
                }
                if ("users".equalsIgnoreCase(table)) {
                    continue;
                }
                refs.add(new FkRef(schema, table, column));
            }
        }
        return refs.stream().distinct().toList();
    }

    private static long deleteByFk(Connection conn, FkRef ref, List<UUID> userIds) throws Exception {
        if (userIds.isEmpty()) {
            return 0;
        }
        String sql = "delete from " + q(ref.schema) + "." + q(ref.table) + " where " + q(ref.column) + " in (" + placeholders(userIds.size()) + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < userIds.size(); i++) {
                ps.setObject(i + 1, userIds.get(i));
            }
            return ps.executeUpdate();
        }
    }

    private static long deleteUsers(Connection conn, List<UUID> userIds) throws Exception {
        if (userIds.isEmpty()) {
            return 0;
        }
        String sql = "delete from users where id in (" + placeholders(userIds.size()) + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < userIds.size(); i++) {
                ps.setObject(i + 1, userIds.get(i));
            }
            return ps.executeUpdate();
        }
    }

    private static long executeOrderedCleanup(Connection conn, List<UUID> userIds) throws Exception {
        if (userIds.isEmpty()) {
            return 0;
        }

        String ids = "(" + placeholders(userIds.size()) + ")";
        List<String> sqls = List.of(
                "delete from admin_auth_challenges where session_id in (select id from admin_sessions where user_id in " + ids + ")",
                "delete from admin_auth_challenges where user_id in " + ids,
                "delete from admin_passkeys where user_id in " + ids,
                "delete from admin_notification_dispatches where actor_user_id in " + ids,
                "delete from admin_permission_assignments where user_id in " + ids,
                "delete from admin_audit_logs where actor_user_id in " + ids,
                "delete from admin_sessions where user_id in " + ids,

                "delete from notice_chat_message_images where message_id in (select id from notice_chat_messages where sender_user_id in " + ids + ")",
                "update notice_chat_read_receipts set last_read_message_id = null where last_read_message_id in (select id from notice_chat_messages where sender_user_id in " + ids + ")",
                "update notice_chat_room_participant_states set last_read_message_id = null where last_read_message_id in (select id from notice_chat_messages where sender_user_id in " + ids + ")",
                "update notice_chat_messages set reply_to_message_id = null where reply_to_message_id in (select id from notice_chat_messages where sender_user_id in " + ids + ")",
                "delete from notice_chat_messages where sender_user_id in " + ids,
                "delete from notice_chat_message_images where message_id in (select id from notice_chat_messages where room_id in (select id from notice_chat_rooms where owner_user_id in " + ids + " or guest_user_id in " + ids + "))",
                "update notice_chat_read_receipts set last_read_message_id = null where room_id in (select id from notice_chat_rooms where owner_user_id in " + ids + " or guest_user_id in " + ids + ")",
                "update notice_chat_room_participant_states set last_read_message_id = null where room_id in (select id from notice_chat_rooms where owner_user_id in " + ids + " or guest_user_id in " + ids + ")",
                "delete from notice_chat_messages where room_id in (select id from notice_chat_rooms where owner_user_id in " + ids + " or guest_user_id in " + ids + ")",
                "delete from notice_chat_read_receipts where room_id in (select id from notice_chat_rooms where owner_user_id in " + ids + " or guest_user_id in " + ids + ")",
                "delete from notice_chat_room_participant_states where room_id in (select id from notice_chat_rooms where owner_user_id in " + ids + " or guest_user_id in " + ids + ")",
                "delete from notice_chat_rooms where owner_user_id in " + ids + " or guest_user_id in " + ids,

                "delete from community_comments where author_id in " + ids,
                "delete from community_post_reactions where user_id in " + ids,
                "delete from community_post_votes where user_id in " + ids,
                "delete from community_post_images where post_id in (select id from community_posts where author_id in " + ids + ")",
                "delete from community_post_tags where post_id in (select id from community_posts where author_id in " + ids + ")",
                "delete from reports where target_type = 'COMMUNITY_POST' and target_id in (select id from community_posts where author_id in " + ids + ")",
                "delete from community_posts where author_id in " + ids,

                "delete from pet_notice_images where notice_id in (select id from pet_notices where author_id in " + ids + ")",
                "delete from reports where target_type = 'PET_NOTICE' and target_id in (select id from pet_notices where author_id in " + ids + ")",
                "delete from pet_notices where author_id in " + ids,

                "delete from user_blocks where blocker_id in " + ids + " or blocked_id in " + ids,
                "delete from user_follows where follower_id in " + ids + " or following_id in " + ids,
                "delete from user_social_accounts where user_id in " + ids,
                "delete from user_fcm_tokens where user_id in " + ids,
                "delete from user_notification_settings where user_id in " + ids,
                "delete from notifications where user_id in " + ids + " or actor_user_id in " + ids,
                "delete from reports where reporter_id in " + ids,
                "delete from notice_bookmarks where user_id in " + ids
        );

        long total = 0;
        for (String sql : sqls) {
            total += executeWithUserIds(conn, sql, userIds);
        }
        return total;
    }

    private static long executeWithUserIds(Connection conn, String sql, List<UUID> userIds) throws Exception {
        int count = countPlaceholders(sql);
        if (count == 0) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                return ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                ps.setObject(i + 1, userIds.get(i % userIds.size()));
            }
            return ps.executeUpdate();
        }
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    private static String placeholders(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be > 0");
        }
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static List<String> normalizeEmails(String[] args) {
        Set<String> deduped = new LinkedHashSet<>();
        if (args == null) {
            return List.of();
        }
        for (String arg : args) {
            if (isBlank(arg)) {
                continue;
            }
            String v = arg.trim().toLowerCase();
            if (!v.isEmpty()) {
                deduped.add(v);
            }
        }
        return new ArrayList<>(deduped);
    }

    private static Map<String, String> loadEnv(Path envPath) {
        Map<String, String> map = new HashMap<>();
        if (envPath == null || !Files.exists(envPath)) {
            return map;
        }
        try {
            for (String line : Files.readAllLines(envPath)) {
                String s = line == null ? "" : line.trim();
                if (s.isEmpty() || s.startsWith("#") || !s.contains("=")) {
                    continue;
                }
                int idx = s.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String k = s.substring(0, idx).trim();
                String v = s.substring(idx + 1).trim();
                map.put(k, v);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read .env", e);
        }
        return map;
    }

    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) {
            return a.trim();
        }
        if (!isBlank(b)) {
            return b.trim();
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String q(String ident) {
        Objects.requireNonNull(ident, "ident");
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}