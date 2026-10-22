package com.dataanalytics.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "Cloud-Native Data Analytics Platform API");
        response.put("status", "UP");
        response.put("version", "1.0.0");
        response.put("cloudInfrastructure", "AWS (Singapore) + Serverless Neon PostgreSQL");
        response.put("timestamp", OffsetDateTime.now());

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("datasets", "/api/v1/datasets");
        endpoints.put("health", "/actuator/health");
        response.put("endpoints", endpoints);

        return ResponseEntity.ok(response);
    }
}
