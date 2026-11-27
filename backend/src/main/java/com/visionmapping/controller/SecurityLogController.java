package com.visionmapping.controller;

import com.visionmapping.dto.response.SecurityLogResponse;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityLogService;
import com.visionmapping.security.audit.SecurityOutcome;
import java.time.LocalDate;
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** V10 (SEC-09-05): the security log for admins. Read-only: there is no endpoint that changes it. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/security-events", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Authorities.VIEW_SECURITY_LOG + "')")
@Tag(name = "Admin: security log", description = "The security log")
public class SecurityLogController {

    private final SecurityLogService service;

    @Operation(summary = "Search the security log")
    @GetMapping
    public ResponseEntity<SecurityLogResponse> search(
            @RequestParam(required = false) SecurityEventType type,
            @RequestParam(required = false) SecurityOutcome outcome,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(service.search(type, outcome, actorId, from, to, page, size));
    }
}
