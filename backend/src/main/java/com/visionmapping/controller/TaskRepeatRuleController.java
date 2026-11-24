package com.visionmapping.controller;

import com.visionmapping.dto.request.StreamlineRequests;
import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.service.TaskHistoryService;
import com.visionmapping.service.TaskRepeatRuleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V27 SR-FR-09: repeating task rules, and a task's status history (SR-FR-06). */
@RestController
@RequiredArgsConstructor
public class TaskRepeatRuleController {

    private final TaskRepeatRuleService service;
    private final TaskHistoryService historyService;

    @GetMapping("/api/task-repeat-rules")
    public List<StreamlineResponses.RepeatRule> list() {
        return service.listRules();
    }

    @PostMapping("/api/task-repeat-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public StreamlineResponses.RepeatRule create(@RequestBody StreamlineRequests.CreateRule request) {
        if (request.taskId() == null || request.cadence() == null) {
            throw new com.visionmapping.exception.BusinessRuleException("Choose a task and how often it repeats.");
        }
        return TaskRepeatRuleService.toResponse(service.repeatTask(request.taskId(), request.cadence(), request.firstDue()));
    }

    @PutMapping("/api/task-repeat-rules/{id}")
    public StreamlineResponses.RepeatRule update(@PathVariable Long id, @RequestBody StreamlineRequests.UpdateRule request) {
        return service.updateRule(id, request.cadence(), request.active());
    }

    /** Stops the rule; tasks it already made are kept. */
    @DeleteMapping("/api/task-repeat-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable Long id) {
        service.stopRule(id);
    }

    @GetMapping("/api/tasks/{id}/history")
    public List<StreamlineResponses.StatusChange> history(@PathVariable Long id) {
        return historyService.history(id);
    }
}
