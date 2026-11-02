package com.visionmapping.controller;

import com.visionmapping.dto.request.UiTextOverrideRequest;
import com.visionmapping.dto.response.UiTextOverrideResponse;
import com.visionmapping.service.UiTextService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-20: interface text changes. Reading is public; changing needs the ADMIN role. */
@RestController
@RequiredArgsConstructor
public class UiTextController {

    private final UiTextService service;

    @GetMapping("/api/ui-text")
    public Map<String, String> overrides(@RequestParam String language) {
        return service.overridesFor(language);
    }

    @GetMapping("/api/admin/ui-text")
    public List<UiTextOverrideResponse> list(@RequestParam String language) {
        return service.list(language);
    }

    @PutMapping("/api/admin/ui-text")
    public UiTextOverrideResponse save(@Valid @RequestBody UiTextOverrideRequest request) {
        return service.save(request);
    }

    @DeleteMapping("/api/admin/ui-text/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable Long id) {
        service.reset(id);
    }
}
