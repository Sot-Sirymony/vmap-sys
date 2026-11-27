package com.visionmapping.controller;

import com.visionmapping.dto.response.ModuleJourneyResponses.Journey;
import com.visionmapping.dto.response.ModuleJourneyResponses.ModuleNext;
import com.visionmapping.service.ModuleJourneyService;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RequestParam;

/** The module journey by rule, with no AI. Split from AiModuleController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/modules", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Module journey", description = "Which module comes next, by rule")
public class ModuleJourneyController {

    private final ModuleJourneyService journey;

    @Operation(summary = "Get the next module by rule")
    @GetMapping("/next")
    public ResponseEntity<ModuleNext> nextByRule(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(journey.next(language));
    }

    @Operation(summary = "Get my journey through the modules")
    @GetMapping("/journey")
    public ResponseEntity<Journey> journey(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(journey.journey(language));
    }
}
