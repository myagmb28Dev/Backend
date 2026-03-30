package com.example.pogun.controller.user;

import com.example.pogun.dto.user.UpdateProfileRequest;
import com.example.pogun.dto.user.UserCommunityPostSummaryResponse;
import com.example.pogun.dto.user.UserPetNoticeSummaryResponse;
import com.example.pogun.dto.user.UserProfileResponse;
import com.example.pogun.service.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
        given(userService.getProfile()).willReturn(new UserProfileResponse(
                UUID.randomUUID(),
                "user@example.com",
                "포근",
                "https://example.com/profile.png",
                "010-1234-5678",
                "GOOGLE",
                List.of("GOOGLE"),
                "USER",
                "ACTIVE"
        ));

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
        given(userService.updateProfile(any(UpdateProfileRequest.class))).willReturn(new UserProfileResponse(
                UUID.randomUUID(),
                "user@example.com",
                "수정닉",
                "https://example.com/profile.png",
                "010-1234-5678",
                "GOOGLE",
                List.of("GOOGLE"),
                "USER",
                "ACTIVE"
        ));

        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("프로필 수정 성공"))
                .andExpect(jsonPath("$.data.nickname").value("수정닉"))
                .andDo(print());
    }

    @Test
    @DisplayName("프로필 수정 파일 첨부 성공")
    void updateProfileWithFileSuccess() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("수정닉");
        request.setPhoneNumber("010-1234-5678");
        given(userService.updateProfile(any(UpdateProfileRequest.class), any(org.springframework.web.multipart.MultipartFile.class))).willReturn(new UserProfileResponse(
                UUID.randomUUID(),
                "user@example.com",
                "수정닉",
                "/uploads/profile/users/11111111-1111-1111-1111-111111111111/profile.png",
                "010-1234-5678",
                "GOOGLE",
                List.of("GOOGLE"),
                "USER",
                "ACTIVE"
        ));

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                "application/json",
                objectMapper.writeValueAsBytes(request)
        );
        MockMultipartFile imagePart = new MockMultipartFile(
                "profileImage",
                "profile.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        );

        mockMvc.perform(multipart("/api/users/me")
                        .file(requestPart)
                        .file(imagePart)
                        .with(requestBuilder -> {
                            requestBuilder.setMethod("PATCH");
                            return requestBuilder;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("프로필 수정 성공"))
                .andExpect(jsonPath("$.data.nickname").value("수정닉"))
                .andDo(print());
    }

    @Test
    @DisplayName("내 실종 공고 목록 조회 성공")
    void myPostsSuccess() throws Exception {
        given(userService.myPetNotices()).willReturn(List.of(new UserPetNoticeSummaryResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "실종 공고",
                "DOG",
                "말티즈",
                Instant.parse("2026-03-20T10:00:00Z"),
                "서울",
                "OPEN",
                3L,
                Instant.parse("2026-03-20T12:00:00Z")
        )));

        mockMvc.perform(get("/api/users/me/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("실종 공고 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].title").value("실종 공고"))
                .andDo(print());
    }

    @Test
    @DisplayName("내 커뮤니티 글 목록 조회 성공")
    void myCommunityPostsSuccess() throws Exception {
        given(userService.myCommunityPosts()).willReturn(List.of(new UserCommunityPostSummaryResponse(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "커뮤니티 글",
                "ACTIVE",
                7L,
                Instant.parse("2026-03-20T12:00:00Z")
        )));

        mockMvc.perform(get("/api/users/me/community-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].title").value("커뮤니티 글"))
                .andDo(print());
    }
}