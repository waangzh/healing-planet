package com.example.demos.web.controller;

import com.example.demos.web.common.properties.InternalApiProperties;
import com.example.demos.web.pojo.vo.PlantStateVO;
import com.example.demos.web.service.PlantStateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@RestController
@RequestMapping("/internal/plant-state")
public class InternalPlantStateController {
    private final PlantStateService plantStateService;
    private final InternalApiProperties properties;

    public InternalPlantStateController(PlantStateService plantStateService, InternalApiProperties properties) {
        this.plantStateService = plantStateService;
        this.properties = properties;
    }

    @GetMapping("/{plantInstanceId}")
    public ResponseEntity<?> state(@PathVariable Integer plantInstanceId,
                                   @RequestParam Long userId,
                                   @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        if (!authorized(apiKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Optional<PlantStateVO> state = plantStateService.getState(plantInstanceId, userId);
        return state.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean authorized(String actual) {
        String expected = properties.getKey();
        if (expected == null || expected.trim().isEmpty() || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
