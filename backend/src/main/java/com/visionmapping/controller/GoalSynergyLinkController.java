package com.visionmapping.controller;

import com.visionmapping.dto.request.GoalSynergyLinkRequest;
import com.visionmapping.dto.response.GoalSynergyLinkResponse;
import com.visionmapping.service.GoalSynergyLinkService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-35.1/35.2: a goal's synergy links. Nested under the goal for list/create;
 * delete is by link id.
 */
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalSynergyLinkController {

    private final GoalSynergyLinkService service;

    @GetMapping("/{id}/synergy-links")
    public List<GoalSynergyLinkResponse> list(@PathVariable Long id) {
        return service.listLinks(id);
    }

    @PostMapping("/{id}/synergy-links")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalSynergyLinkResponse create(@PathVariable Long id, @Valid @RequestBody GoalSynergyLinkRequest request) {
        return service.createLink(id, request);
    }

    @DeleteMapping("/synergy-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long linkId) {
        service.deleteLink(linkId);
    }
}
