package com.visionmapping.controller;

import com.visionmapping.dto.request.AttitudeCheckInRequest;
import com.visionmapping.dto.response.AttitudeCheckInResponse;
import com.visionmapping.service.AttitudeCheckInService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import com.visionmapping.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/attitude-checkins", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Attitude check-ins", description = "The daily attitude check-in")
public class AttitudeCheckInController {

    private final AttitudeCheckInService service;

    @Operation(summary = "List attitude check-ins, newest first, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<AttitudeCheckInResponse>> list(
            @PageableDefault(size = 30, sort = "checkInDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.pageCheckIns(pageable));
    }

    @Operation(summary = "Save the attitude check-in for a day")
    @PutMapping(value = "/{date}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AttitudeCheckInResponse> save(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody AttitudeCheckInRequest request) {
        return ResponseEntity.ok(service.saveForDate(date, request));
    }
}
