package com.visionmapping.controller;

import com.visionmapping.dto.request.CoachCommentRequest;
import com.visionmapping.dto.request.PitchRatingRequest;
import com.visionmapping.dto.response.CoachCommentResponse;
import com.visionmapping.dto.response.CoachParticipantDetailResponse;
import com.visionmapping.dto.response.CoachParticipantResponse;
import com.visionmapping.dto.response.PitchReviewResponse;
import com.visionmapping.service.CoachService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V9: the coach workspace. The service requires COACH or ADMIN and checks each participant. */
@RestController
@RequestMapping("/api/coach/participants")
@RequiredArgsConstructor
public class CoachController {

    private final CoachService service;

    @GetMapping
    public List<CoachParticipantResponse> participants() {
        return service.participants();
    }

    @GetMapping("/{id}")
    public CoachParticipantDetailResponse participant(@PathVariable Long id) {
        return service.participant(id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CoachCommentResponse comment(@PathVariable Long id, @Valid @RequestBody CoachCommentRequest request) {
        return service.comment(id, request);
    }

    @PutMapping("/{id}/pitch-rating")
    public PitchReviewResponse ratePitch(@PathVariable Long id, @Valid @RequestBody PitchRatingRequest request) {
        return service.ratePitch(id, request);
    }
}
