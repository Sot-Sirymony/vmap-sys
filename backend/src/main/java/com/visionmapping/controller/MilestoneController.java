package com.visionmapping.controller;

import com.visionmapping.dto.request.MilestoneRequest;
import com.visionmapping.dto.response.MilestoneResponse;
import com.visionmapping.service.MilestoneService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService service;

    @GetMapping
    public List<MilestoneResponse> list(@RequestParam Long dreamId) {
        return service.listForDream(dreamId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MilestoneResponse create(@Valid @RequestBody MilestoneRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archive(id);
    }
}
