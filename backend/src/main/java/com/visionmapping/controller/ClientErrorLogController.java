package com.visionmapping.controller;

import com.visionmapping.dto.request.ClientErrorLogRequest;
import com.visionmapping.dto.response.ClientErrorLogPageResponse;
import com.visionmapping.dto.response.ClientErrorLogResponse;
import com.visionmapping.service.ClientErrorLogService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Diagnostics: requests that failed in the browser. A user records and reads their own; admins read everyone's. */
@RestController
@RequiredArgsConstructor
public class ClientErrorLogController {

    private final ClientErrorLogService service;

    @PostMapping("/api/client-errors")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@Valid @RequestBody ClientErrorLogRequest request) {
        service.record(request);
    }

    @GetMapping("/api/client-errors")
    public List<ClientErrorLogResponse> listMine(@RequestParam String page) {
        return service.listMine(page);
    }

    @GetMapping("/api/admin/client-errors")
    public ClientErrorLogPageResponse search(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(page, status, pageNumber, size);
    }
}
