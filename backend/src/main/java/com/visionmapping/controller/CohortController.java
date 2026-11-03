package com.visionmapping.controller;

import com.visionmapping.dto.request.JoinCohortRequest;
import com.visionmapping.dto.request.JournalSharingRequest;
import com.visionmapping.dto.response.CoachCommentResponse;
import com.visionmapping.dto.response.MyCohortResponse;
import com.visionmapping.service.CoachCommentService;
import com.visionmapping.service.CohortService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** V9: the participant's side of cohorts, journal sharing and coach comments. */
@RestController
@RequiredArgsConstructor
public class CohortController {

    private final CohortService cohortService;
    private final CoachCommentService coachCommentService;

    @GetMapping("/api/cohort")
    public MyCohortResponse mine() {
        return cohortService.mine();
    }

    @PostMapping("/api/cohort/join")
    public MyCohortResponse join(@Valid @RequestBody JoinCohortRequest request) {
        return cohortService.join(request);
    }

    @PostMapping("/api/cohort/leave")
    public MyCohortResponse leave() {
        return cohortService.leave();
    }

    @PutMapping("/api/cohort/sharing")
    public MyCohortResponse sharing(@RequestBody JournalSharingRequest request) {
        return cohortService.updateSharing(request);
    }

    @GetMapping("/api/coach-comments")
    public List<CoachCommentResponse> comments() {
        return coachCommentService.mine();
    }

    @PostMapping("/api/coach-comments/{id}/read")
    public CoachCommentResponse markRead(@PathVariable Long id) {
        return coachCommentService.markRead(id);
    }
}
