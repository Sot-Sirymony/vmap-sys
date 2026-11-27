package com.visionmapping.controller;

import com.visionmapping.dto.response.CoachCommentResponse;
import com.visionmapping.service.CoachCommentService;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** V9: comments my coach left for me. Split from CohortController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/coach-comments", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Coach comments", description = "Comments my coach left for me")
public class CoachCommentController {

    private final CoachCommentService coachCommentService;

    @Operation(summary = "List my coach's comments")
    @GetMapping
    public ResponseEntity<List<CoachCommentResponse>> comments() {
        return ResponseEntity.ok(coachCommentService.mine());
    }

    @Operation(summary = "Mark a comment as read")
    @PostMapping("/{id}/read")
    public ResponseEntity<CoachCommentResponse> markRead(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(coachCommentService.markRead(id));
    }
}
