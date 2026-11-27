package com.visionmapping.controller;

import com.visionmapping.dto.request.SavedReportCommentaryRequest;
import com.visionmapping.dto.request.SavedReportRequest;
import com.visionmapping.dto.request.SavedReportSectionsRequest;
import com.visionmapping.service.report.ReportSection;
import com.visionmapping.dto.response.CommentaryDraftResponse;
import com.visionmapping.dto.response.ReportRowsResponse;
import com.visionmapping.dto.response.SavedReportResponse;
import com.visionmapping.mapper.ReportRowsMapper;
import com.visionmapping.dto.request.CoachShareRequest;
import com.visionmapping.dto.request.ReportShareRequest;
import com.visionmapping.dto.response.CoachShareResponse;
import com.visionmapping.dto.response.ReportExportResponse;
import com.visionmapping.dto.response.ReportShareResponse;
import com.visionmapping.service.ReportCoachShareService;
import com.visionmapping.service.ReportShareService;
import com.visionmapping.dto.response.ReportReminderResponse;
import com.visionmapping.service.SavedReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import com.visionmapping.service.ReportActionsService;
import com.visionmapping.web.FileDownloads;
import com.visionmapping.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import com.visionmapping.web.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;

/**
 * V19 (BRD 19.0.0, RM-FR-16 to RM-FR-31): saved Management Reports, scoped to
 * the signed-in participant. A final report answers 409 to any change but a
 * rename, and to delete (RMC-09).
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/reports/saved", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Saved reports", description = "Draft and final management reports, their downloads and shares")
public class SavedReportController {

    private final SavedReportService service;
    private final ReportShareService shares;
    private final ReportCoachShareService coachShares;
    private final ReportActionsService actions;

    @Operation(summary = "List saved reports, newest change first, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<SavedReportResponse>> list(
            @RequestParam(required = false) @Pattern(regexp = "DRAFT|FINAL") String status,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) @Size(max = 120) String q,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.page(status, includeArchived, q, pageable));
    }

    @Operation(summary = "Create a saved report")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SavedReportResponse> create(@Valid @RequestBody SavedReportRequest request) {
        SavedReportResponse created = service.create(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a saved report")
    @GetMapping("/{id}")
    public ResponseEntity<SavedReportResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Operation(summary = "Update a saved report")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SavedReportResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody SavedReportRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @Operation(summary = "Update a report's commentary")
    @PutMapping(value = "/{id}/commentary", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SavedReportResponse> updateCommentary(@PathVariable @Positive Long id, @Valid @RequestBody SavedReportCommentaryRequest request) {
        return ResponseEntity.ok(service.updateCommentary(id, request));
    }

    /** RM-FR-39: the sections the report's files include, remembered for the next download; allowed on a final report. */
    @Operation(summary = "Choose a report's sections")
    @PutMapping(value = "/{id}/sections", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SavedReportResponse> updateSections(@PathVariable @Positive Long id, @Valid @RequestBody SavedReportSectionsRequest request) {
        return ResponseEntity.ok(service.updateSections(id, request.sections()));
    }

    /** RM-FR-49: the in-app report reminder. */
    @Operation(summary = "Get the report reminder")
    @GetMapping("/reminder")
    public ResponseEntity<ReportReminderResponse> reminder() {
        return ResponseEntity.ok(service.reminder());
    }

    @Operation(summary = "Draft commentary by rules or with AI")
    @PostMapping("/{id}/commentary-draft")
    public ResponseEntity<CommentaryDraftResponse> commentaryDraft(@PathVariable @Positive Long id,
                                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                                   @RequestParam(defaultValue = "RULES") @Pattern(regexp = "RULES|AI") String mode) {
        // RM-FR-28 fixed rules, or RM-FR-29 Draft with AI.
        return ResponseEntity.ok(actions.commentaryDraft(id, language, mode));
    }

    @Operation(summary = "Make a report final")
    @PostMapping("/{id}/finalize")
    public ResponseEntity<SavedReportResponse> finalizeReport(@PathVariable @Positive Long id,
                                              @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.finalizeReport(id, language));
    }

    @Operation(summary = "Copy a report")
    @PostMapping("/{id}/copy")
    public ResponseEntity<SavedReportResponse> copy(@PathVariable @Positive Long id) {
        SavedReportResponse created = service.copy(id);
        return ApiResponses.createdAt("/reports/saved", created.id(), created);
    }

    @Operation(summary = "Archive a report")
    @PatchMapping("/{id}/archive")
    public ResponseEntity<SavedReportResponse> archive(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.archive(id, true));
    }

    @Operation(summary = "Restore an archived report")
    @PatchMapping("/{id}/restore")
    public ResponseEntity<SavedReportResponse> restore(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.archive(id, false));
    }

    /** RM-FR-22: drafts only; a final report answers 409. */
    @Operation(summary = "Delete a draft report")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get a report's rows")
    @GetMapping("/{id}/rows")
    public ResponseEntity<ReportRowsResponse> rows(@PathVariable @Positive Long id,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(ReportRowsMapper.toResponse(service.rows(id, language)));
    }

    @Operation(summary = "Download a report as Excel")
    @GetMapping(value = "/{id}.xlsx", produces = FileDownloads.XLSX)
    public ResponseEntity<ByteArrayResource> excel(@PathVariable @Positive Long id,
                                                   @RequestParam(required = false) @Pattern(regexp = "en|km") String language,
                                                   @RequestParam(required = false) List<ReportSection> sections) {
        return FileDownloads.attachment(actions.savedExcel(id, language, sections), FileDownloads.XLSX);
    }

    /** RM-FR-42: the PDF, from the snapshot once final. */
    @Operation(summary = "Download a report as a PDF")
    @GetMapping(value = "/{id}.pdf", produces = FileDownloads.PDF)
    public ResponseEntity<ByteArrayResource> pdf(@PathVariable @Positive Long id,
                                                 @RequestParam(required = false) @Pattern(regexp = "en|km") String language,
                                                 @RequestParam(required = false) List<ReportSection> sections) {
        return FileDownloads.attachment(actions.savedPdf(id, language, sections), FileDownloads.PDF);
    }

    /** RM-FR-25: the report's downloads, newest first. */
    @Operation(summary = "List a report's downloads")
    @GetMapping("/{id}/exports")
    public ResponseEntity<List<ReportExportResponse>> exports(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.exports(id));
    }

    /** RM-FR-46: the report's share links. */
    /** RM-FR-50: whether this report is shared with the participant's coach. */
    @Operation(summary = "Whether a report is shared with my coach")
    @GetMapping("/{id}/coach-share")
    public ResponseEntity<CoachShareResponse> coachShare(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(coachShares.status(id));
    }

    /** RM-FR-50: share a final report with the coach, or change the sections shared. */
    @Operation(summary = "Share a final report with my coach")
    @PutMapping(value = "/{id}/coach-share", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CoachShareResponse> shareWithCoach(@PathVariable @Positive Long id, @Valid @RequestBody CoachShareRequest request) {
        return ResponseEntity.ok(coachShares.share(id, request));
    }

    /** RM-FR-50: stop sharing with the coach at once. */
    @Operation(summary = "Stop sharing a report with my coach")
    @DeleteMapping("/{id}/coach-share")
    public ResponseEntity<CoachShareResponse> unshareWithCoach(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(coachShares.unshare(id));
    }

    @Operation(summary = "List a report's share links")
    @GetMapping("/{id}/shares")
    public ResponseEntity<List<ReportShareResponse>> shares(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(shares.list(id));
    }

    /** RM-FR-46, RM-FR-48: a read-only link to a final report; 20 a day per account (RM-NFR-07). */
    @Operation(summary = "Make a share link for a final report")
    @PostMapping(value = "/{id}/shares", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportShareResponse> share(@PathVariable @Positive Long id, @Valid @RequestBody ReportShareRequest request) {
        ReportShareResponse created = actions.share(id, request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Revoke a share link")
    @PatchMapping("/{id}/shares/{shareId}/revoke")
    public ResponseEntity<ReportShareResponse> revokeShare(@PathVariable @Positive Long id, @PathVariable @Positive Long shareId) {
        return ResponseEntity.ok(shares.revoke(id, shareId));
    }

    @Operation(summary = "Download a report as slides")
    @GetMapping(value = "/{id}.pptx", produces = FileDownloads.PPTX)
    public ResponseEntity<ByteArrayResource> deck(@PathVariable @Positive Long id,
                                                  @RequestParam(required = false) @Pattern(regexp = "en|km") String language,
                                                  @RequestParam(required = false) @Size(max = 80) String title,
                                                  @RequestParam(defaultValue = "true") boolean includeName,
                                                  @RequestParam(required = false) List<ReportSection> sections) {
        return FileDownloads.attachment(actions.savedDeck(id, language, title, includeName, sections), FileDownloads.PPTX);
    }
}
