package com.example.pogun.service.shelterpet;

import com.example.pogun.dto.shelterpet.ShelterPetListResponse;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.shelterpet.ShelterPetRepository;
import com.example.pogun.service.ai.AiService;
import com.example.pogun.service.cache.AiSourceCacheService;
import com.example.pogun.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShelterPetServiceTest {

    @Mock
    private ShelterPublicApiClient shelterPublicApiClient;
    @Mock
    private ShelterPetRepository shelterPetRepository;
    @Mock
    private PetNoticeRepository petNoticeRepository;
    @Mock
    private AiService aiService;
    @Mock
    private AiSourceCacheService aiSourceCacheService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ShelterPetService shelterPetService;

    @BeforeEach
    void setUp() {
        when(aiSourceCacheService.currentVersion(anyString())).thenReturn("1");
        when(aiSourceCacheService.getOrLoad(anyString(), any(Duration.class), eq(ShelterPetListResponse.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ShelterPetListResponse> loader = invocation.getArgument(3);
                    return loader.get();
                });
    }

    @Test
    void getShelterPetList_withoutRegionReturnsNationwideItems() {
        ShelterPublicApiClient.ShelterPublicApiAnimal seoul = animal("1001", "서울특별시 강남구");
        ShelterPublicApiClient.ShelterPublicApiAnimal busan = animal("1002", "부산광역시 해운대구");
        when(shelterPublicApiClient.fetchShelterPets(eq(null), eq(null), eq(null), eq("LATEST"), eq(0), anyInt()))
                .thenReturn(new ShelterPublicApiClient.ShelterPublicApiPage(1, 20, 2, List.of(seoul, busan)));
        when(shelterPetRepository.findAllById(List.of("1001", "1002"))).thenReturn(List.of());

        ShelterPetListResponse response = shelterPetService.getShelterPetList(null, null, null, "LATEST", 0, 20);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.items()).extracting("id").containsExactly("1001", "1002");
        assertThat(response.filters().region()).isNull();
        verify(shelterPublicApiClient).fetchShelterPets(null, null, null, "LATEST", 0, 20);
    }

    @Test
    void getShelterPetList_withRegionFiltersItems() {
        ShelterPublicApiClient.ShelterPublicApiAnimal seoul = animal("1001", "서울특별시 강남구");
        ShelterPublicApiClient.ShelterPublicApiAnimal busan = animal("1002", "부산광역시 해운대구");
        when(shelterPublicApiClient.fetchShelterPets(eq(null), eq(null), eq(null), eq("LATEST"), eq(0), anyInt()))
                .thenReturn(new ShelterPublicApiClient.ShelterPublicApiPage(1, 20, 2, List.of(seoul, busan)));
        when(shelterPetRepository.findAllById(List.of("1001"))).thenReturn(List.of());

        ShelterPetListResponse response = shelterPetService.getShelterPetList("서울", null, null, "LATEST", 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).extracting("id").containsExactly("1001");
        assertThat(response.filters().region()).isEqualTo("서울");
    }

    @Test
    void searchShelterPetsMatchesTitleAndDescriptionFields() {
        ShelterPublicApiClient.ShelterPublicApiAnimal gentle = animal("1001", "서울특별시 강남구", "온순하고 사람을 잘 따름");
        ShelterPublicApiClient.ShelterPublicApiAnimal shy = animal("1002", "부산광역시 해운대구", "겁이 많음");
        when(shelterPublicApiClient.fetchShelterPets(eq(null), eq(null), eq(null), eq("LATEST"), eq(0), anyInt()))
                .thenReturn(new ShelterPublicApiClient.ShelterPublicApiPage(1, 20, 2, List.of(gentle, shy)));
        when(shelterPetRepository.findAllById(List.of("1001"))).thenReturn(List.of());

        ShelterPetListResponse response = shelterPetService.searchShelterPets("온순", null, null, null, "LATEST", 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).extracting("id").containsExactly("1001");
    }

    private ShelterPublicApiClient.ShelterPublicApiAnimal animal(String id, String organizationName) {
        return animal(id, organizationName, "온순함");
    }

    private ShelterPublicApiClient.ShelterPublicApiAnimal animal(String id, String organizationName, String specialMark) {
        return new ShelterPublicApiClient.ShelterPublicApiAnimal(
                id,
                "NOTICE-" + id,
                "공고중",
                "보호센터",
                "010-0000-0000",
                organizationName + " 보호소",
                organizationName,
                "믹스",
                "[개] 믹스",
                specialMark,
                organizationName + " 인근",
                "20260520",
                "20260520",
                "20260530",
                "20260520",
                List.of("https://cdn.example.com/" + id + ".jpg")
        );
    }
}
