package com.visionmapping.controller;

import com.visionmapping.dto.response.SecurityLogResponse;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityLogService;
import com.visionmapping.security.audit.SecurityOutcome;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** V10 (SEC-09-05): the security log for admins. Read-only: there is no endpoint that changes it. */
@RestController
@RequestMapping("/api/admin/security-events")
@RequiredArgsConstructor
public class SecurityLogController {

    private final SecurityLogService service;

    @GetMapping
    public SecurityLogResponse search(
            @RequestParam(required = false) SecurityEventType type,
            @RequestParam(required = false) SecurityOutcome outcome,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(type, outcome, actorId, from, to, page, size);
    }
}
