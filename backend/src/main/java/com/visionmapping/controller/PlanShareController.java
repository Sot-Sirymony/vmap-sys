package com.visionmapping.controller;

import com.visionmapping.dto.request.PlanShareRequest;
import com.visionmapping.dto.response.PlanShareResponse;
import com.visionmapping.dto.response.SharedPlanResponse;
import com.visionmapping.service.PlanShareService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * V9 (v0.1 FR-10): the owner manages share links under /api/plan-shares; a
 * partner opens one under /api/shared-plans without signing in.
 */
@RestController
@RequiredArgsConstructor
public class PlanShareController {

    private final PlanShareService service;

    @GetMapping("/api/plan-shares")
    public List<PlanShareResponse> list(@RequestParam(required = false) Long dreamId) {
        return service.list(dreamId);
    }

    @PostMapping("/api/plan-shares")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanShareResponse create(@Valid @RequestBody PlanShareRequest request) {
        return service.create(request);
    }

    @PostMapping("/api/plan-shares/{id}/revoke")
    public PlanShareResponse revoke(@PathVariable Long id) {
        return service.revoke(id);
    }

    @GetMapping("/api/shared-plans/{token}")
    public SharedPlanResponse view(@PathVariable String token) {
        return service.view(token);
    }
}
