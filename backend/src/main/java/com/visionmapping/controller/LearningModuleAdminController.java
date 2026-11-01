package com.visionmapping.controller;

import com.visionmapping.dto.request.ModuleContentRequest;
import com.visionmapping.dto.request.ModuleOrderRequest;
import com.visionmapping.dto.response.ModuleContentResponse;
import com.visionmapping.service.LearningModuleAdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/modules")
@RequiredArgsConstructor
public class LearningModuleAdminController {

    private final LearningModuleAdminService service;

    @GetMapping
    public List<ModuleContentResponse> list(@RequestParam(defaultValue = "en") String language) {
        return service.listContent(language);
    }

    @PutMapping("/{number}/content/{language}")
    public ModuleContentResponse update(
            @PathVariable Integer number,
            @PathVariable String language,
            @Valid @RequestBody ModuleContentRequest request) {
        return service.updateContent(number, language, request);
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody ModuleOrderRequest request) {
        service.reorder(request.orderedNumbers());
    }
}
