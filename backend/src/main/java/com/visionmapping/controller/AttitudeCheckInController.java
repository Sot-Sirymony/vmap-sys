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

@RestController
@RequestMapping("/api/attitude-checkins")
@RequiredArgsConstructor
public class AttitudeCheckInController {

    private final AttitudeCheckInService service;

    @GetMapping
    public List<AttitudeCheckInResponse> list() {
        return service.listRecent();
    }

    @PutMapping("/{date}")
    public AttitudeCheckInResponse save(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody AttitudeCheckInRequest request) {
        return service.saveForDate(date, request);
    }
}
