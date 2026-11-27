package com.visionmapping.controller;

import com.visionmapping.dto.response.PagedResponse;
import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.service.TaskHistoryService;
import jakarta.validation.constraints.Positive;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** V27 SR-FR-06: a task's status history. Split from TaskRepeatRuleController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Task history", description = "When a task changed status")
public class TaskHistoryController {

    private final TaskHistoryService historyService;

    @Operation(summary = "List a task's status changes, oldest first, one page at a time")
    @GetMapping("/{id}/history")
    public ResponseEntity<PagedResponse<StreamlineResponses.StatusChange>> history(
            @PathVariable @Positive Long id,
            @PageableDefault(size = 50, sort = "changedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(historyService.pageHistory(id, pageable));
    }
}
