package com.visionmapping.controller;

import com.visionmapping.dto.request.JoinCohortRequest;
import com.visionmapping.dto.request.JournalSharingRequest;
import com.visionmapping.dto.response.MyCohortResponse;
import com.visionmapping.service.CohortService;
import com.visionmapping.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** V9: the participant's side of cohorts and journal sharing. Coach comments are in {@link CoachCommentController}. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/cohort", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "My cohort", description = "Joining a cohort and sharing journals with the coach")
public class CohortController {

    private final CohortService cohortService;

    @Operation(summary = "Get my cohort")
    @GetMapping
    public ResponseEntity<MyCohortResponse> mine() {
        return ResponseEntity.ok(cohortService.mine());
    }

    /** V10 (SEC-06-02, SEC-06-03): join codes are limited per account and per network address. */
    @Operation(summary = "Join a cohort with its code")
    @PostMapping(value = "/join", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MyCohortResponse> join(@Valid @RequestBody JoinCohortRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(cohortService.joinFrom(request, ClientIp.of(http)));
    }

    @Operation(summary = "Leave my cohort")
    @PostMapping("/leave")
    public ResponseEntity<MyCohortResponse> leave() {
        return ResponseEntity.ok(cohortService.leave());
    }

    @Operation(summary = "Choose which journals my coach may read")
    @PutMapping(value = "/sharing", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MyCohortResponse> sharing(@Valid @RequestBody JournalSharingRequest request) {
        return ResponseEntity.ok(cohortService.updateSharing(request));
    }
}
