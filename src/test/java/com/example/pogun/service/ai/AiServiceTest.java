package com.example.pogun.service.ai;

import com.example.pogun.dto.ai.AiAnalysisResultCallbackRequest;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.repository.ai.AiAnalysisRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.shelterpet.ShelterPetRepository;
import com.example.pogun.service.shelterpet.ShelterPublicApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiAnalysisRepository aiAnalysisRepository;

    @Mock
    private PetNoticeRepository petNoticeRepository;

    @Mock
    private ShelterPetRepository shelterPetRepository;

    @Mock
    private ShelterPublicApiClient shelterPublicApiClient;

    @InjectMocks
    private AiService aiService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiService, "aiApiKey", "secret-key");
    }

    @Test
    void saveMissingPetAnalysisResult_rejectsInvalidKeyBeforeNoticeLookup() {
        AiAnalysisResultCallbackRequest request = new AiAnalysisResultCallbackRequest();

        assertThatThrownBy(() -> aiService.saveMissingPetAnalysisResult("not-a-uuid", "wrong-key", request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getCode()).isEqualTo("AI_API_FORBIDDEN");
                });

        verifyNoInteractions(petNoticeRepository, aiAnalysisRepository, shelterPetRepository, shelterPublicApiClient);
    }

    @Test
    void saveShelterAnalysisResult_rejectsInvalidKeyBeforeRepositoryOrExternalLookup() {
        AiAnalysisResultCallbackRequest request = new AiAnalysisResultCallbackRequest();

        assertThatThrownBy(() -> aiService.saveShelterAnalysisResult("shelter-1", "wrong-key", request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getCode()).isEqualTo("AI_API_FORBIDDEN");
                });

        verifyNoInteractions(shelterPetRepository, shelterPublicApiClient, aiAnalysisRepository, petNoticeRepository);
    }
}
