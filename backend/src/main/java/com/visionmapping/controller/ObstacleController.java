package com.visionmapping.controller;

import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.service.VisionMappingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/obstacles")
@RequiredArgsConstructor
public class ObstacleController {

    private final VisionMappingService service;

    @GetMapping
    public List<ObstacleResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listObstacles(includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObstacleResponse create(@Valid @RequestBody ObstacleRequest request) {
        return service.createObstacle(request);
    }

    @GetMapping("/{id}")
    public ObstacleResponse get(@PathVariable Long id) {
        return service.getObstacle(id);
    }

    @PutMapping("/{id}")
    public ObstacleResponse update(@PathVariable Long id, @Valid @RequestBody ObstacleRequest request) {
        return service.updateObstacle(id, request);
    }

    @PatchMapping("/{id}/status")
    public ObstacleResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateObstacleStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archiveObstacle(id);
    }
    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreObstacle(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteObstacle(id);
    }

}
