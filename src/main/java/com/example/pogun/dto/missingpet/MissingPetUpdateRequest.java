package com.example.pogun.dto.missingpet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Schema(description = "Missing pet notice update request")
public class MissingPetUpdateRequest {
    @Schema(description = "Notice title", example = "Please help find my maltese")
    private String title;

    @Schema(
            description = "Animal type in lowercase snake_case. Examples: dog, cat, rabbit, guinea_pig, parrot, turtle, snake, fish",
            example = "dog"
    )
    private String animalType;

    @Schema(description = "Breed", example = "Maltese")
    private String breed;

    @Schema(description = "Gender", example = "MALE")
    private String gender;

    @PositiveOrZero
    @Schema(description = "Age", example = "3")
    private Integer age;

    @Schema(description = "Color", example = "WHITE")
    private String color;

    @Schema(description = "Description", example = "Wearing a yellow collar")
    private String description;

    @Schema(description = "Missing date time", example = "2026-03-18T10:00:00Z")
    private String missingDate;

    @Schema(description = "Missing region", example = "Seoul Gangnam-gu")
    private String missingRegion;

    @Schema(description = "Missing address detail", example = "Near Samseong Station")
    private String missingAddress;

    @PositiveOrZero
    @Schema(description = "Reward amount", example = "500000")
    private Integer rewardAmount;

    @Schema(description = "Contact phone", example = "010-1111-2222")
    private String contactPhone;

    @Schema(description = "Notice status", example = "OPEN")
    private String status;

    public Map<String, Object> toRequestMap() {
        Map<String, Object> request = new LinkedHashMap<>();
        putIfNotNull(request, "title", title);
        putIfNotNull(request, "animalType", animalType);
        putIfNotNull(request, "breed", breed);
        putIfNotNull(request, "gender", gender);
        putIfNotNull(request, "age", age);
        putIfNotNull(request, "color", color);
        putIfNotNull(request, "description", description);
        putIfNotNull(request, "missingDate", missingDate);
        putIfNotNull(request, "missingRegion", missingRegion);
        putIfNotNull(request, "missingAddress", missingAddress);
        putIfNotNull(request, "rewardAmount", rewardAmount);
        putIfNotNull(request, "contactPhone", contactPhone);
        putIfNotNull(request, "status", status);
        return request;
    }

    private void putIfNotNull(Map<String, Object> request, String key, Object value) {
        if (value != null) {
            request.put(key, value);
        }
    }
}
