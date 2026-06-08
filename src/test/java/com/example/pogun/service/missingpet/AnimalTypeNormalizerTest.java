package com.example.pogun.service.missingpet;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnimalTypeNormalizerTest {

    @Test
    void normalizesCanonicalAndLegacyAnimalTypes() {
        assertThat(AnimalTypeNormalizer.normalize("dog")).isEqualTo("dog");
        assertThat(AnimalTypeNormalizer.normalize("DOG")).isEqualTo("dog");
        assertThat(AnimalTypeNormalizer.normalize("guinea pig")).isEqualTo("guinea_pig");
        assertThat(AnimalTypeNormalizer.normalize("Prairie-Dog")).isEqualTo("prairie_dog");
    }

    @Test
    void normalizesAliasesUsedByClients() {
        assertThat(AnimalTypeNormalizer.normalize("puppy")).isEqualTo("dog");
        assertThat(AnimalTypeNormalizer.normalize("kitten")).isEqualTo("cat");
        assertThat(AnimalTypeNormalizer.normalize("raccoon dog")).isEqualTo("raccoon_dog");
    }

    @Test
    void normalizesStoredValuesForResponses() {
        assertThat(AnimalTypeNormalizer.normalizeForResponse("DOG")).isEqualTo("dog");
        assertThat(AnimalTypeNormalizer.normalizeForResponse("guinea pig")).isEqualTo("guinea_pig");
        assertThat(AnimalTypeNormalizer.normalizeForResponse("unknown-animal")).isEqualTo("unknown-animal");
    }

    @Test
    void rejectsUnsupportedAnimalTypes() {
        assertThatThrownBy(() -> AnimalTypeNormalizer.normalize("dragon"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("지원하지 않는 animalType");
    }
}
