package com.visionmapping.controller;

import com.visionmapping.dto.request.ReorderRequest;
import com.visionmapping.dto.request.SimpleReorderRequest;
import com.visionmapping.dto.request.TaskSplitRequest;
import com.visionmapping.dto.request.TaskStatusUpdateRequest;
import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.TaskSplitResponse;
import com.visionmapping.service.TaskItemService;
import com.visionmapping.service.support.ArchiveFilter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.visionmapping.dto.request.ArchiveRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import com.visionmapping.web.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import com.visionmapping.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Tasks under a step")
public class TaskItemController {

    private final TaskItemService service;

    @Operation(summary = "List tasks, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<TaskItemResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(service.pageTasks(ArchiveFilter.of(includeArchived), pageable));
    }

    @Operation(summary = "Create a task")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskItemResponse> create(@Valid @RequestBody TaskItemRequest request) {
        TaskItemResponse created = service.createTask(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a task")
    @GetMapping("/{id}")
    public ResponseEntity<TaskItemResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getTask(id));
    }

    @Operation(summary = "Update a task")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskItemResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody TaskItemRequest request) {
        return ResponseEntity.ok(service.updateTask(id, request));
    }

    @Operation(summary = "Change a task's status")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskItemResponse> updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody TaskStatusUpdateRequest request) {
        // V17 BL-FR-27: changeNote is kept when the task leaves Blocked.
        return ResponseEntity.ok(service.updateTaskStatus(id, request.status(), request.changeNote()));
    }

    /** V17 BL-FR-25: breaks this task into 2 to 5 smaller tasks under the same step. */
    @Operation(summary = "Split a task into smaller tasks")
    @PostMapping(value = "/{id}/split", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskSplitResponse> split(@PathVariable @Positive Long id, @Valid @RequestBody TaskSplitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.splitTask(id, request));
    }

    @Operation(summary = "Reorder tasks")
    @PatchMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        service.reorderTasks(request.parentId(), request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    /** Tasks page: the participant's own order across all steps (V70). */
    @Operation(summary = "Reorder the task list")
    @PatchMapping(value = "/list-order", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorderList(@Valid @RequestBody SimpleReorderRequest request) {
        service.reorderTaskList(request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive a task")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveTask(id);
        return ResponseEntity.noContent().build();
    }

    /** V29 AR-FR-04: archive with an optional reason (BR-29-1); the DELETE above keeps working without one. */
    @Operation(summary = "Archive a task, with an optional reason")
    @PostMapping(value = "/{id}/archive", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> archive(@PathVariable @Positive Long id, @Valid @RequestBody(required = false) ArchiveRequest request) {
        service.archiveTask(id, ArchiveRequest.reasonOf(request));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived task")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreTask(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a task permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteTask(id);
        return ResponseEntity.noContent().build();
    }

}
