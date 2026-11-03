package com.visionmapping.controller;

import com.visionmapping.dto.request.CohortMemberRequest;
import com.visionmapping.dto.request.CohortRequest;
import com.visionmapping.dto.response.CohortMemberResponse;
import com.visionmapping.dto.response.CohortResponse;
import com.visionmapping.service.CohortAdminService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V9: cohort management. The service requires the ADMIN role. */
@RestController
@RequestMapping("/api/admin/cohorts")
@RequiredArgsConstructor
public class CohortAdminController {

    private final CohortAdminService service;

    @GetMapping
    public List<CohortResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CohortResponse create(@Valid @RequestBody CohortRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CohortResponse update(@PathVariable Long id, @Valid @RequestBody CohortRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/join-code")
    public CohortResponse newJoinCode(@PathVariable Long id) {
        return service.newJoinCode(id);
    }

    @PostMapping("/{id}/archive")
    public CohortResponse archive(@PathVariable Long id) {
        return service.setArchived(id, true);
    }

    @PostMapping("/{id}/restore")
    public CohortResponse restore(@PathVariable Long id) {
        return service.setArchived(id, false);
    }

    @GetMapping("/{id}/members")
    public List<CohortMemberResponse> members(@PathVariable Long id) {
        return service.members(id);
    }

    @PostMapping("/{id}/members")
    public CohortMemberResponse addMember(@PathVariable Long id, @Valid @RequestBody CohortMemberRequest request) {
        return service.addMember(id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        service.removeMember(id, userId);
    }
}
