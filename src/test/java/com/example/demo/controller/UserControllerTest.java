package com.example.demo.controller;

import com.example.demo.dto.UpdateProfileRequest;
import com.example.demo.service.UserService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
    }

    @Test
    @DisplayName("프로필 조회 성공")
    void getProfileSuccess() throws Exception {
        given(userService.getProfile()).willReturn(Map.of("nickname", "포근"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("프로필 조회 성공"))
                .andExpect(jsonPath("$.data.nickname").value("포근"))
                .andDo(print());
    }

    @Test
    @DisplayName("프로필 수정 성공")
    void updateProfileSuccess() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("수정닉");
        request.setPhoneNumber("010-1234-5678");
        request.setProfileImageUrl("https://example.com/profile.png");
        given(userService.updateProfile(any(UpdateProfileRequest.class))).willReturn(Map.of("nickname", "수정닉"));

        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("프로필 수정 성공"))
                .andExpect(jsonPath("$.data.nickname").value("수정닉"))
                .andDo(print());
    }

    @Test
    @DisplayName("내 실종 공고 목록 조회 성공")
    void myPostsSuccess() throws Exception {
        given(userService.myPetNotices()).willReturn(List.of(Map.of("id", "notice-1")));

        mockMvc.perform(get("/api/users/me/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value("notice-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("내 커뮤니티 글 목록 조회 성공")
    void myCommunityPostsSuccess() throws Exception {
        given(userService.myCommunityPosts()).willReturn(List.of(Map.of("id", "post-1")));

        mockMvc.perform(get("/api/users/me/community-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value("post-1"))
                .andDo(print());
    }
}
