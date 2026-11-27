package com.visionmapping.controller;

import com.visionmapping.dto.response.ClientErrorLogResponse;
import com.visionmapping.dto.response.PagedResponse;
import com.visionmapping.service.ClientErrorLogService;
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
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Everyone's failed requests, for support. Split from ClientErrorLogController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/client-errors", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Admin: failed requests", description = "Everyone's failed requests, newest first")
public class ClientErrorLogAdminController {

    private final ClientErrorLogService service;

    @Operation(summary = "Search everyone's failed requests, newest first, one page at a time")
    @GetMapping
    @PreAuthorize("hasAuthority('" + Authorities.HANDLE_SUPPORT + "')")
    public ResponseEntity<PagedResponse<ClientErrorLogResponse>> search(
            @RequestParam(name = "pageKey", required = false) @Size(max = 200) String pageKey,
            @RequestParam(required = false) Integer status,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(service.search(pageKey, status, pageable));
    }
}
