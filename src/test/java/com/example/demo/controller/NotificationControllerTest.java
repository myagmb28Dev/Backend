package com.example.demo.controller;

import com.example.demo.service.NotificationService;
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

import java.util.List;
import java.util.Map;

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
        given(notificationService.getNotifications()).willReturn(List.of(Map.of("id", "noti-1")));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value("noti-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("알림 읽음 처리 성공")
    void markReadSuccess() throws Exception {
        given(notificationService.markNotificationAsRead("noti-1")).willReturn(Map.of("id", "noti-1", "read", true));

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", "noti-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 읽음 처리 성공"))
                .andExpect(jsonPath("$.data.read").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("전체 알림 읽음 처리 성공")
    void markReadAllSuccess() throws Exception {
        given(notificationService.markAllNotificationsAsRead()).willReturn(Map.of("updatedCount", 3));

        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 전체 읽음 처리 성공"))
                .andExpect(jsonPath("$.data.updatedCount").value(3))
                .andDo(print());
    }

    @Test
    @DisplayName("FCM 토큰 갱신 성공")
    void upsertFcmTokenSuccess() throws Exception {
        Map<String, String> request = Map.of("token", "fcm-token", "platform", "ANDROID");
        given(notificationService.upsertFcmToken(request)).willReturn(Map.of("saved", true));

        mockMvc.perform(post("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("FCM 토큰 갱신 성공"))
                .andExpect(jsonPath("$.data.saved").value(true))
                .andDo(print());
    }
}
