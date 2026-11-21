package com.visionmapping.controller;

import com.visionmapping.dto.request.SavedReportCommentaryRequest;
import com.visionmapping.dto.request.SavedReportRequest;
import com.visionmapping.dto.request.SavedReportSectionsRequest;
import com.visionmapping.service.report.ReportSection;
import com.visionmapping.dto.response.CommentaryDraftResponse;
import com.visionmapping.dto.response.ReportRowsResponse;
import com.visionmapping.dto.response.SavedReportResponse;
import com.visionmapping.mapper.ReportRowsMapper;
import com.visionmapping.dto.request.ReportShareRequest;
import com.visionmapping.dto.response.ReportExportResponse;
import com.visionmapping.dto.response.ReportShareResponse;
import com.visionmapping.service.ReportShareService;
import com.visionmapping.dto.response.ReportReminderResponse;
import com.visionmapping.service.SavedReportService;
import com.visionmapping.service.ai.ReportCommentaryAiService;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.util.UserScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * V19 (BRD 19.0.0, RM-FR-16 to RM-FR-31): saved Management Reports, scoped to
 * the signed-in participant. A final report answers 409 to any change but a
 * rename, and to delete (RMC-09).
 */
@RestController
@Validated
@RequestMapping("/api/reports/saved")
@RequiredArgsConstructor
public class SavedReportController {

    private final SavedReportService service;
    private final ReportShareService shares;
    private final ReportCommentaryAiService aiDrafts;
    private final RequestRateLimiter rateLimiter;
    private final UserScope userScope;

    @GetMapping
    public List<SavedReportResponse> list(@RequestParam(required = false) @Pattern(regexp = "DRAFT|FINAL") String status,
                                          @RequestParam(defaultValue = "false") boolean includeArchived,
                                          @RequestParam(required = false) @Size(max = 120) String q) {
        return service.list(status, includeArchived, q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedReportResponse create(@Valid @RequestBody SavedReportRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public SavedReportResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public SavedReportResponse update(@PathVariable Long id, @Valid @RequestBody SavedReportRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/commentary")
    public SavedReportResponse updateCommentary(@PathVariable Long id, @Valid @RequestBody SavedReportCommentaryRequest request) {
        return service.updateCommentary(id, request);
    }

    /** RM-FR-39: the sections the report's files include, remembered for the next download; allowed on a final report. */
    @PutMapping("/{id}/sections")
    public SavedReportResponse updateSections(@PathVariable Long id, @Valid @RequestBody SavedReportSectionsRequest request) {
        return service.updateSections(id, request.sections());
    }

    /** RM-FR-49: the in-app report reminder. */
    @GetMapping("/reminder")
    public ReportReminderResponse reminder() {
        return service.reminder();
    }

    @PostMapping("/{id}/commentary-draft")
    public CommentaryDraftResponse commentaryDraft(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                                   @RequestParam(defaultValue = "RULES") @Pattern(regexp = "RULES|AI") String mode) {
        // RM-FR-28 fixed rules, or RM-FR-29 Draft with AI.
        return "AI".equals(mode) ? aiDrafts.draft(id, language) : service.commentaryDraft(id, language);
    }

    @PostMapping("/{id}/finalize")
    public SavedReportResponse finalizeReport(@PathVariable Long id,
                                              @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.finalizeReport(id, language);
    }

    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedReportResponse copy(@PathVariable Long id) {
        return service.copy(id);
    }

    @PatchMapping("/{id}/archive")
    public SavedReportResponse archive(@PathVariable Long id) {
        return service.archive(id, true);
    }

    @PatchMapping("/{id}/restore")
    public SavedReportResponse restore(@PathVariable Long id) {
        return service.archive(id, false);
    }

    /** RM-FR-22: drafts only; a final report answers 409. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/rows")
    public ReportRowsResponse rows(@PathVariable Long id,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ReportRowsMapper.toResponse(service.rows(id, language));
    }

    @GetMapping("/{id}.xlsx")
    public ResponseEntity<ByteArrayResource> excel(@PathVariable Long id,
                                                   @RequestParam(required = false) @Pattern(regexp = "en|km") String language,
                                                   @RequestParam(required = false) List<ReportSection> sections) {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_EXPORT, "user:" + userScope.currentUser().getId());
        return ManagementReportController.download(service.excel(id, language, sections), ManagementReportController.XLSX);
    }

    /** RM-FR-42: the PDF, from the snapshot once final. */
    @GetMapping("/{id}.pdf")
    public ResponseEntity<ByteArrayResource> pdf(@PathVariable Long id,
                                                 @RequestParam(required = false) @Pattern(regexp = "en|km") String language,
                                                 @RequestParam(required = false) List<ReportSection> sections) {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_EXPORT, "user:" + userScope.currentUser().getId());
        return ManagementReportController.download(service.pdf(id, language, sections), ManagementReportController.PDF);
    }

    /** RM-FR-25: the report's downloads, newest first. */
    @GetMapping("/{id}/exports")
    public List<ReportExportResponse> exports(@PathVariable Long id) {
        return service.exports(id);
    }

    /** RM-FR-46: the report's share links. */
    @GetMapping("/{id}/shares")
    public List<ReportShareResponse> shares(@PathVariable Long id) {
        return shares.list(id);
    }

    /** RM-FR-46, RM-FR-48: a read-only link to a final report; 20 a day per account (RM-NFR-07). */
    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportShareResponse share(@PathVariable Long id, @Valid @RequestBody ReportShareRequest request) {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_SHARE, "user:" + userScope.currentUser().getId());
        return shares.create(id, request);
    }

    @PatchMapping("/{id}/shares/{shareId}/revoke")
    public ReportShareResponse revokeShare(@PathVariable Long id, @PathVariable Long shareId) {
        return shares.revoke(id, shareId);
    }

    @GetMapping("/{id}.pptx")
    public ResponseEntity<ByteArrayResource> deck(@PathVariable Long id,
                                                  @RequestParam(required = false) @Pattern(regexp = "en|km") String language,
                                                  @RequestParam(required = false) @Size(max = 80) String title,
                                                  @RequestParam(defaultValue = "true") boolean includeName,
                                                  @RequestParam(required = false) List<ReportSection> sections) {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_EXPORT, "user:" + userScope.currentUser().getId());
        return ManagementReportController.download(service.deck(id, language, title, includeName, sections),
                ManagementReportController.PPTX);
    }
}
