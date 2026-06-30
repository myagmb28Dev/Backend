package com.example.pogun.controller.missingpet;

import com.example.pogun.controller.common.MultipartJsonRequestParser;
import com.example.pogun.dto.missingpet.MissingPetDetailResponse;
import com.example.pogun.service.missingpet.MissingPetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissingPetControllerMultipartRequestTest {

    private MissingPetService missingPetService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        missingPetService = mock(MissingPetService.class);
        MultipartJsonRequestParser parser = new MultipartJsonRequestParser(
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new MissingPetController(missingPetService, parser))
                .build();
    }

    @Test
    void createWithFilesAcceptsTextPlainJsonRequestPart() throws Exception {
        when(missingPetService.createMissingPet(any(), any())).thenReturn(detail());

        MockMultipartFile request = new MockMultipartFile(
                "request",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                """
                        {
                          "title": "Codex missing pet upload test",
                          "animalType": "dog",
                          "missingDate": "2026-06-30T09:00:00Z",
                          "missingRegion": "Seoul"
                        }
                        """.getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile image = new MockMultipartFile(
                "images",
                "pet.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/missing-pets").file(request).file(image))
                .andExpect(status().isCreated());

        verify(missingPetService).createMissingPet(
                org.mockito.ArgumentMatchers.argThat(map -> "Codex missing pet upload test".equals(map.get("title"))),
                org.mockito.ArgumentMatchers.argThat(files -> files.size() == 1)
        );
    }

    private MissingPetDetailResponse detail() {
        return new MissingPetDetailResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex missing pet upload test",
                "dog",
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-30T09:00:00Z"),
                "Seoul",
                null,
                null,
                null,
                "OPEN",
                "Open",
                false,
                0L,
                0L,
                false,
                false,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "tester",
                Instant.parse("2026-06-30T09:00:00Z"),
                Instant.parse("2026-06-30T09:00:00Z"),
                List.of()
        );
    }
}
