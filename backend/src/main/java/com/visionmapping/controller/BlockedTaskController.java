package com.visionmapping.controller;

import com.visionmapping.dto.response.ObstacleHelpResponses.BlockedTask;
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

/** AH-FR-39: Blocked tasks with a blocker reason and no obstacle. No AI. Split from ObstacleHelpController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Blocked tasks", description = "Blocked tasks that still need an obstacle")
public class BlockedTaskController {

    private final ObstacleSupportService supportService;

    @Operation(summary = "List Blocked tasks that have no obstacle yet")
    @GetMapping("/blocked-without-obstacle")
    public ResponseEntity<List<BlockedTask>> blockedWithoutObstacle() {
        return ResponseEntity.ok(supportService.blockedWithoutObstacle());
    }
}
