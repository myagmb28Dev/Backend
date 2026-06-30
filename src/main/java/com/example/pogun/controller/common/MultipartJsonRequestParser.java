package com.example.pogun.controller.common;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MultipartJsonRequestParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public <T> T parse(String request, Class<T> type) {
        T parsed;
        try {
            parsed = objectMapper.readValue(request, type);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("INVALID_REQUEST_BODY", "request JSON could not be parsed.");
        }

        Set<ConstraintViolation<T>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            for (ConstraintViolation<T> violation : violations) {
                detail.put(String.valueOf(violation.getPropertyPath()), violation.getMessage());
            }
            throw ApiException.badRequest("VALIDATION_ERROR", "Request validation failed.", detail);
        }
        return parsed;
    }
}
