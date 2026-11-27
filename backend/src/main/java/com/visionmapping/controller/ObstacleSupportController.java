package com.visionmapping.controller;

import com.visionmapping.dto.response.ObstacleHelpResponses.PartnerMatches;
import com.visionmapping.dto.response.ObstacleHelpResponses.RankedObstacle;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.service.ObstacleSupportService;
import java.util.List;
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

/** V15 Phase 2: obstacle helpers that need no AI (AH-FR-35, AH-FR-41, AH-NFR-03). Split from ObstacleHelpController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/obstacles", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Obstacle support", description = "Obstacle helpers that need no AI")
public class ObstacleSupportController {

    private final ObstacleSupportService supportService;

    /** AH-FR-35: the participant's own partners who fit the obstacle type. Nothing is sent to Claude. */
    @Operation(summary = "List my partners who fit an obstacle type")
    @GetMapping("/partner-matches")
    public ResponseEntity<PartnerMatches> partnerMatches(@RequestParam ObstacleType obstacleType) {
        return ResponseEntity.ok(supportService.partnerMatches(obstacleType));
    }

    /** AH-FR-41: open obstacles in the rule order. Bounded: open obstacles only. */
    @Operation(summary = "List open obstacles in the order to tackle them")
    @GetMapping("/priority")
    public ResponseEntity<List<RankedObstacle>> priority() {
        return ResponseEntity.ok(supportService.priority());
    }
}
