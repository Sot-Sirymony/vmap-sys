package com.visionmapping.controller;

import com.visionmapping.dto.request.GoalSynergyLinkRequest;
import com.visionmapping.dto.response.GoalSynergyLinkResponse;
import com.visionmapping.service.GoalSynergyLinkService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import com.visionmapping.web.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * FR-35.1/35.2: a goal's synergy links. Nested under the goal for list/create;
 * delete is by link id.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/goals", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Goal synergy links", description = "Links between goals that help each other")
public class GoalSynergyLinkController {

    private final GoalSynergyLinkService service;

    @Operation(summary = "List a goal's synergy links")
    @GetMapping("/{id}/synergy-links")
    public ResponseEntity<List<GoalSynergyLinkResponse>> list(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.listLinks(id));
    }

    @Operation(summary = "Link a goal to another goal")
    @PostMapping(value = "/{id}/synergy-links", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoalSynergyLinkResponse> create(@PathVariable @Positive Long id, @Valid @RequestBody GoalSynergyLinkRequest request) {
        GoalSynergyLinkResponse created = service.createLink(id, request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Remove a synergy link")
    @DeleteMapping("/synergy-links/{linkId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long linkId) {
        service.deleteLink(linkId);
        return ResponseEntity.noContent().build();
    }
}
