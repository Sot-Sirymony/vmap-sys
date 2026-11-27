package com.visionmapping.controller;

import com.visionmapping.dto.request.ClientErrorLogRequest;
import com.visionmapping.dto.response.ClientErrorLogResponse;
import com.visionmapping.service.ClientErrorLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Diagnostics: requests that failed in the browser. A user records and reads their own; admins read everyone's in {@link ClientErrorLogAdminController}. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/client-errors", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Failed requests", description = "Requests that failed in my browser")
public class ClientErrorLogController {

    private final ClientErrorLogService service;

    @Operation(summary = "Record a request that failed in the browser")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> record(@Valid @RequestBody ClientErrorLogRequest request) {
        service.record(request);
        return ResponseEntity.noContent().build();
    }

    /** Bounded: the latest failures on one page of the app. */
    @Operation(summary = "List my recent failed requests on one page of the app")
    @GetMapping
    public ResponseEntity<List<ClientErrorLogResponse>> listMine(@RequestParam @Size(max = 200) String page) {
        return ResponseEntity.ok(service.listMine(page));
    }
}
