package com.example.pogun.controller.presence;

import com.example.pogun.controller.common.GlobalExceptionHandler;
import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.user.UserPresenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PresenceRestControllerTest {

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private NoticeChatService noticeChatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectProvider<NoticeChatService> provider = new ObjectProvider<>() {
            @Override
            public NoticeChatService getObject(Object... args) {
                return noticeChatService;
            }

            @Override
            public NoticeChatService getIfAvailable() {
                return noticeChatService;
            }

            @Override
            public NoticeChatService getObject() {
                return noticeChatService;
            }

            @Override
            public NoticeChatService getIfUnique() {
                return noticeChatService;
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new PresenceRestController(userPresenceService, provider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void heartbeat_rejectsNonStringPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new Object(), null, List.of())
        );

        mockMvc.perform(post("/api/presence/heartbeat")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSessionId": "web-1",
                                  "page": "chat"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(userPresenceService, noticeChatService);
    }
}
