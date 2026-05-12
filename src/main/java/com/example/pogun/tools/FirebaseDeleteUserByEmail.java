package com.example.pogun.tools;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

public class FirebaseDeleteUserByEmail {
    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Usage: FirebaseDeleteUserByEmail <email>");
        }
        String email = args[0].trim();
        if (email.isEmpty()) {
            throw new IllegalArgumentException("email is required");
        }

        String keyBase64 = System.getenv("FIREBASE_KEY_BASE64");
        if (keyBase64 == null || keyBase64.isBlank()) {
            // Try loading from .env like other tools
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (java.nio.file.Files.exists(envPath)) {
                try {
                    for (String line : java.nio.file.Files.readAllLines(envPath)) {
                        String s = line == null ? "" : line.trim();
                        if (s.isEmpty() || s.startsWith("#") || !s.contains("=")) continue;
                        int idx = s.indexOf('=');
                        if (idx <= 0) continue;
                        String k = s.substring(0, idx).trim();
                        String v = s.substring(idx + 1).trim();
                        if ("FIREBASE_KEY_BASE64".equals(k) && !v.isEmpty()) {
                            keyBase64 = v;
                            break;
                        }
                    }
                } catch (Exception ex) {
                    // ignore and fallthrough
                }
            }
        }
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException("FIREBASE_KEY_BASE64 env var is required");
        }

        byte[] decoded = Base64.getDecoder().decode(keyBase64);
        try (InputStream is = new ByteArrayInputStream(decoded)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(is))
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        try {
            UserRecord u = auth.getUserByEmail(email);
            String uid = u.getUid();
            System.out.println("Found Firebase user: uid=" + uid + " email=" + email);
            auth.deleteUser(uid);
            System.out.println("Deleted Firebase user: " + uid);
        } catch (FirebaseAuthException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("user-not-found") || msg.contains("no user record")) {
                System.out.println("No Firebase user found for email: " + email);
            } else {
                throw new RuntimeException("Failed to delete Firebase user: " + e.getMessage(), e);
            }
        }
    }
}
