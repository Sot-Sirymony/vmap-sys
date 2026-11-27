package com.visionmapping.controller;

import com.visionmapping.dto.request.CoachCommentRequest;
import com.visionmapping.dto.request.PitchRatingRequest;
import com.visionmapping.dto.response.CoachCommentResponse;
import com.visionmapping.dto.response.CoachParticipantDetailResponse;
import com.visionmapping.dto.response.CoachParticipantResponse;
import com.visionmapping.dto.response.PitchReviewResponse;
import com.visionmapping.dto.response.CoachSharedReportResponse;
import com.visionmapping.dto.response.SharedReportResponse;
import com.visionmapping.service.CoachService;
import com.visionmapping.service.ReportCoachShareService;
import jakarta.validation.Valid;
import java.util.List;
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** V9: the coach workspace. The service requires COACH or ADMIN and checks each participant. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/coach/participants", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Authorities.COACH_COHORTS + "')")
@Tag(name = "Coach", description = "A coach's read-only view of their participants")
public class CoachController {

    private final CoachService service;
    private final ReportCoachShareService reportShares;
    private final com.visionmapping.service.CoachStreamlineService coachStreamline;

    /** V19 RM-FR-50: the final reports this participant shared with the coach. */
    @Operation(summary = "List the reports a participant shared with me")
    @GetMapping("/{id}/reports")
    public ResponseEntity<List<CoachSharedReportResponse>> sharedReports(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(reportShares.sharedWithMe(id));
    }

    /** V19 RM-FR-50: one shared report, read-only, with only the shared sections. */
    @Operation(summary = "Open a report a participant shared with me")
    @GetMapping("/{id}/reports/{reportId}")
    public ResponseEntity<SharedReportResponse> sharedReport(@PathVariable @Positive Long id, @PathVariable @Positive Long reportId) {
        return ResponseEntity.ok(reportShares.view(id, reportId));
    }

    /** V27 D-27-10: the participant's Streamline reviews, read-only. */
    @Operation(summary = "List a participant's Streamline reviews")
    @GetMapping("/{id}/streamline-reviews")
    public ResponseEntity<List<com.visionmapping.dto.response.StreamlineResponses.ListItem>> streamlineReviews(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(coachStreamline.reviewsOf(id));
    }

    /** V27 D-27-10: one of the participant's Streamline reviews, read-only. */
    @Operation(summary = "Open a participant's Streamline review")
    @GetMapping("/{id}/streamline-reviews/{reviewId}")
    public ResponseEntity<com.visionmapping.dto.response.StreamlineResponses.Review> streamlineReview(@PathVariable @Positive Long id, @PathVariable @Positive Long reviewId) {
        return ResponseEntity.ok(coachStreamline.reviewOf(id, reviewId));
    }

    @Operation(summary = "List my participants")
    @GetMapping
    public ResponseEntity<List<CoachParticipantResponse>> participants() {
        return ResponseEntity.ok(service.participants());
    }

    @Operation(summary = "Get a participant")
    @GetMapping("/{id}")
    public ResponseEntity<CoachParticipantDetailResponse> participant(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.participant(id));
    }

    @Operation(summary = "Leave a comment for a participant")
    @PostMapping(value = "/{id}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CoachCommentResponse> comment(@PathVariable @Positive Long id, @Valid @RequestBody CoachCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.comment(id, request));
    }

    @Operation(summary = "Rate a participant's pitch")
    @PutMapping(value = "/{id}/pitch-rating", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PitchReviewResponse> ratePitch(@PathVariable @Positive Long id, @Valid @RequestBody PitchRatingRequest request) {
        return ResponseEntity.ok(service.ratePitch(id, request));
    }
}
