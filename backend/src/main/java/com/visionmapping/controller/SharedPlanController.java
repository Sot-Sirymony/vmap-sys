package com.visionmapping.controller;

import com.visionmapping.dto.response.SharedPlanResponse;
import com.visionmapping.service.PlanShareService;
import com.visionmapping.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
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

/**
 * V9 (v0.1 FR-10): a partner opens a shared plan without signing in. Read-only;
 * the 256-bit token in the path is the key. Split from PlanShareController in V33.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/shared-plans", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Shared plans", description = "A shared dream plan, opened through its link without signing in")
public class SharedPlanController {

    private final PlanShareService service;

    /** V10 (SEC-06-02): opening share links is limited per network address. */
    @Operation(summary = "Open a shared plan through its link")
    @GetMapping("/{token}")
    public ResponseEntity<SharedPlanResponse> view(@PathVariable @Size(max = 200) String token, HttpServletRequest http) {
        return ResponseEntity.ok(service.viewFrom(token, ClientIp.of(http)));
    }
}
