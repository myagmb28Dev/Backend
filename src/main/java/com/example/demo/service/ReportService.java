package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    public Map<String, Object> createReport(Map<String, Object> request) {
        return Map.of(
                "id", "report-1",
                "status", "RECEIVED",
                "targetType", request.getOrDefault("targetType", "COMMUNITY_POST"),
                "targetId", request.getOrDefault("targetId", "post-1")
        );
    }
}
