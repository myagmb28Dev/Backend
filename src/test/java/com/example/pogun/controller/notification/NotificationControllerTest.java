package com.example.pogun.controller.notification;

import com.example.pogun.dto.notification.NotificationFcmTokenRequest;
import com.example.pogun.dto.notification.NotificationFcmTokenResponse;
import com.example.pogun.dto.notification.NotificationReadAllResponse;
import com.example.pogun.dto.notification.NotificationResponse;
import com.example.pogun.service.notification.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController).build();
    }

    @Test
    @DisplayName("알림 목록 조회 성공")
    void listSuccess() throws Exception {
        UUID notificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440033");
        given(notificationService.getNotifications()).willReturn(List.of(
                new NotificationResponse(notificationId, "NEW_NOTICE", "PET_NOTICE", UUID.randomUUID(), "제목", "본문", false, Instant.parse("2026-03-20T10:00:00Z"))
        ));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value(notificationId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("알림 읽음 처리 성공")
    void markReadSuccess() throws Exception {
        UUID notificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440034");
        given(notificationService.markNotificationAsRead("noti-1")).willReturn(
                new NotificationResponse(notificationId, "NEW_NOTICE", "PET_NOTICE", UUID.randomUUID(), "제목", "본문", true, Instant.parse("2026-03-20T10:00:00Z"))
        );

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", "noti-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 읽음 처리 성공"))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("전체 알림 읽음 처리 성공")
    void markReadAllSuccess() throws Exception {
        given(notificationService.markAllNotificationsAsRead()).willReturn(new NotificationReadAllResponse(3));

        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 전체 읽음 처리 성공"))
                .andExpect(jsonPath("$.data.updatedCount").value(3))
                .andDo(print());
    }

    @Test
    @DisplayName("FCM 토큰 갱신 성공")
    void upsertFcmTokenSuccess() throws Exception {
        NotificationFcmTokenRequest request = new NotificationFcmTokenRequest();
        request.setToken("fcm-token");
        request.setPlatform("ANDROID");
        given(notificationService.upsertFcmToken(anyMap())).willReturn(new NotificationFcmTokenResponse(UUID.randomUUID(), "fcm-token", "ANDROID", null, true));

        mockMvc.perform(post("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("FCM 토큰 갱신 성공"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andDo(print());
    }
}
