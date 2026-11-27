package com.visionmapping.controller;

import com.visionmapping.service.HealthService;
import java.util.Map;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/** The platform health check (Render, ZAP). Never versioned (V33 D-33-1), and public. */
@RestController
@RequestMapping(value = ApiPaths.LEGACY, produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Health", description = "Whether the service is up")
public class HealthController {

    private final HealthService healthService;

    @Operation(summary = "Whether the service and its cache are up")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(healthService.health());
    }
}
