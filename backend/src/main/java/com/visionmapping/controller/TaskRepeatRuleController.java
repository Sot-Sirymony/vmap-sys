package com.visionmapping.controller;

import com.visionmapping.dto.request.StreamlineRequests;
import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.service.TaskRepeatRuleService;
import com.visionmapping.web.ApiResponses;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** V27 SR-FR-09: repeating task rules. A task's status history is in {@link TaskHistoryController} (V33 D-33-1). */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/task-repeat-rules", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Task repeat rules", description = "Tasks that repeat on a schedule")
public class TaskRepeatRuleController {

    private final TaskRepeatRuleService service;

    @Operation(summary = "List repeat rules")
    @GetMapping
    public ResponseEntity<List<StreamlineResponses.RepeatRule>> list() {
        return ResponseEntity.ok(service.listRules());
    }

    @Operation(summary = "Make a task repeat")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.RepeatRule> create(@Valid @RequestBody StreamlineRequests.CreateRule request) {
        StreamlineResponses.RepeatRule created = service.createRule(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Change a repeat rule's cadence, or stop or restart it")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.RepeatRule> update(@PathVariable @Positive Long id,
                                                                 @Valid @RequestBody StreamlineRequests.UpdateRule request) {
        return ResponseEntity.ok(service.updateRule(id, request.cadence(), request.active()));
    }

    /** Stops the rule; tasks it already made are kept. */
    @Operation(summary = "Stop a repeat rule")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> stop(@PathVariable @Positive Long id) {
        service.stopRule(id);
        return ResponseEntity.noContent().build();
    }
}
