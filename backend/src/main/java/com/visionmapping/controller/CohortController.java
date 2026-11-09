package com.visionmapping.controller;

import jakarta.servlet.http.HttpServletRequest;
import com.visionmapping.util.ClientIp;
import com.visionmapping.util.UserScope;
import com.visionmapping.service.support.RequestRateLimiter;
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
    private final RequestRateLimiter rateLimiter;
    private final UserScope userScope;

    @GetMapping("/api/cohort")
    public MyCohortResponse mine() {
        return cohortService.mine();
    }

    @PostMapping("/api/cohort/join")
    public MyCohortResponse join(@Valid @RequestBody JoinCohortRequest request, HttpServletRequest http) {
        // V10 (SEC-06-02, SEC-06-03): join codes cannot be guessed by repetition,
        // whether from one account or one network address.
        rateLimiter.check(RequestRateLimiter.Action.JOIN_CODE, "user:" + userScope.currentUser().getId());
        rateLimiter.check(RequestRateLimiter.Action.JOIN_CODE, "ip:" + ClientIp.of(http));
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
