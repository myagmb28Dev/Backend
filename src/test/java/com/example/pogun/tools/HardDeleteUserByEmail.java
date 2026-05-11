package com.example.pogun.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class HardDeleteUserByEmail {

    record FkRef(String schema, String table, String column) {
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 1 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Usage: HardDeleteUserByEmail <email>");
        }
        String targetEmail = args[0].trim();

        String dbUrl = requireEnv("DB_URL");
        String dbUser = requireEnv("DB_USERNAME");
        String dbPass = requireEnv("DB_PASSWORD");

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            conn.setAutoCommit(false);
            try {
                UUID userId = resolveUserId(conn, targetEmail);
                if (userId == null) {
                    System.out.println("User not found by email. Nothing to delete. email=" + targetEmail);
                    conn.rollback();
                    return;
                }

                System.out.println("Target user resolved. email=" + targetEmail + " userId=" + userId);

                List<FkRef> refs = loadUserFkRefs(conn);
                System.out.println("FK references to users.id found: " + refs.size());

                long totalDeleted = 0;
                for (FkRef ref : refs) {
                    long deleted = deleteByFk(conn, ref, userId);
                    if (deleted > 0) {
                        System.out.println("Deleted " + deleted + " row(s) from " + q(ref.schema) + "." + q(ref.table) + " where " + q(ref.column) + "=" + userId);
                        totalDeleted += deleted;
                    }
                }

                long deletedUser = deleteUser(conn, userId);
                if (deletedUser != 1) {
                    throw new IllegalStateException("Expected to delete exactly 1 user row but deleted " + deletedUser);
                }
                System.out.println("Deleted user row. userId=" + userId + ", totalFkRowsDeleted=" + totalDeleted);

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static UUID resolveUserId(Connection conn, String email) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("select id from users where lower(email)=lower(?) limit 1")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Object value = rs.getObject(1);
                if (value instanceof UUID uuid) {
                    return uuid;
                }
                return UUID.fromString(String.valueOf(value));
            }
        }
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
                // Skip self-reference just in case.
                if ("users".equalsIgnoreCase(table)) {
                    continue;
                }
                refs.add(new FkRef(schema, table, column));
            }
        }
        // De-dupe (rare but safe).
        return refs.stream().distinct().toList();
    }

    private static long deleteByFk(Connection conn, FkRef ref, UUID userId) throws Exception {
        String sql = "delete from " + q(ref.schema) + "." + q(ref.table) + " where " + q(ref.column) + " = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            return ps.executeUpdate();
        }
    }

    private static long deleteUser(Connection conn, UUID userId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("delete from users where id = ?")) {
            ps.setObject(1, userId);
            return ps.executeUpdate();
        }
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + key);
        }
        return value.trim();
    }

    private static String q(String ident) {
        Objects.requireNonNull(ident, "ident");
        // Basic identifier quoting (no schema injection expected; still escape double-quotes).
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}

