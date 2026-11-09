package com.visionmapping.controller;

import com.visionmapping.dto.request.GapCoverRequest;
import com.visionmapping.dto.request.SelfAssessmentItemRequest;
import com.visionmapping.dto.response.GapCoverRemovalResponse;
import com.visionmapping.dto.response.SelfAssessmentItemResponse;
import com.visionmapping.service.SelfAssessmentService;
import com.visionmapping.service.support.ArchiveFilter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V8.1 FR-79 to FR-83: strengths and weaknesses, and how each important gap is covered. */
@RestController
@RequestMapping("/api/self-assessment-items")
@RequiredArgsConstructor
public class SelfAssessmentItemController {

    private final SelfAssessmentService service;

    @GetMapping
    public List<SelfAssessmentItemResponse> list(@RequestParam(required = false) Long dreamId,
                                                 @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listItems(dreamId, ArchiveFilter.of(includeArchived));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SelfAssessmentItemResponse create(@Valid @RequestBody SelfAssessmentItemRequest request) {
        return service.createItem(request);
    }

    @PutMapping("/{id}")
    public SelfAssessmentItemResponse update(@PathVariable Long id, @Valid @RequestBody SelfAssessmentItemRequest request) {
        return service.updateItem(id, request);
    }

    @PutMapping("/{id}/cover")
    public SelfAssessmentItemResponse setCover(@PathVariable Long id, @Valid @RequestBody GapCoverRequest request) {
        return service.setCover(id, request);
    }

    /** removeLinked=true also archives the partner or step, or removes the exercise row, the cover created. */
    @DeleteMapping("/{id}/cover")
    public GapCoverRemovalResponse removeCover(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean removeLinked) {
        return service.removeCover(id, removeLinked);
    }

    /** Archives the item. removeLinked works as for removing the cover (AC-10). */
    @DeleteMapping("/{id}")
    public GapCoverRemovalResponse delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean removeLinked) {
        return service.archiveItem(id, removeLinked);
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreItem(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteItem(id);
    }
}
