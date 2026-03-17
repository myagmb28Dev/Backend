package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    public List<Map<String, Object>> getNotifications() {
        return List.of(
                Map.of("id", "ntf-1", "title", "새 공고 등록", "isRead", false),
                Map.of("id", "ntf-2", "title", "내 공고에 댓글", "isRead", true)
        );
    }

    public Map<String, Object> markNotificationAsRead(String notificationId) {
        return Map.of("id", notificationId, "isRead", true);
    }

    public Map<String, Object> markAllNotificationsAsRead() {
        return Map.of("updatedCount", 12);
    }

    public Map<String, Object> upsertFcmToken(Map<String, String> request) {
        return Map.of(
                "token", request.getOrDefault("token", "sample-fcm-token"), 
                "platform", request.getOrDefault("platform", "ANDROID")
        );
    }
}
