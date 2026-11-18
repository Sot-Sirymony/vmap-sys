package com.visionmapping.controller;

import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.service.ManagementReportService;
import com.visionmapping.dto.request.ReportQuery;
import com.visionmapping.dto.response.ReportRowsResponse;
import com.visionmapping.mapper.ReportRowsMapper;
import com.visionmapping.util.UserScope;
import com.visionmapping.service.support.RequestRateLimiter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** V18 (BRD 18.0.0): the Management Report under the Vision Map. Read-only and scoped to the signed-in participant. */
@RestController
@Validated
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ManagementReportController {

    private final ManagementReportService service;
    private final RequestRateLimiter rateLimiter;
    private final UserScope userScope;

    /**
     * MR-FR-03, MR-FR-07 to MR-FR-13: the Summary for all dreams, one vision area or one dream. V19 RM-FR-06:
     * {@code period} is a preset (THIS_MONTH by default) or CUSTOM with {@code from} and {@code to}; RM-FR-10:
     * {@code priority}, {@code owner}, {@code health} and {@code includeCompleted} filter it.
     */
    @GetMapping("/management")
    public ManagementReportResponse summary(@Valid @ModelAttribute ReportQuery query) {
        return service.summary(query.toScope());
    }

    /** V19 RM-FR-13 to RM-FR-15: the Details tab, the same rows as the Excel Report sheet. */
    @GetMapping("/management/rows")
    public ReportRowsResponse rows(@Valid @ModelAttribute ReportQuery query,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ReportRowsMapper.toResponse(service.rows(service.build(query.toScope()), language));
    }

    /** MR-FR-15 to MR-FR-22: the Excel report. Nothing is written to disk. */
    @GetMapping("/management.xlsx")
    public ResponseEntity<ByteArrayResource> excel(@Valid @ModelAttribute ReportQuery query,
                                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_EXPORT, "user:" + userScope.currentUser().getId());
        return download(service.excel(query.toScope(), language), XLSX);
    }

    /** MR-FR-23 to MR-FR-31: the PowerPoint deck for a management review. */
    @GetMapping("/management.pptx")
    public ResponseEntity<ByteArrayResource> deck(@Valid @ModelAttribute ReportQuery query,
                                                  @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                                  @RequestParam(required = false) @Size(max = 80) String title,
                                                  @RequestParam(defaultValue = "true") boolean includeName) {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_EXPORT, "user:" + userScope.currentUser().getId());
        return download(service.deck(query.toScope(), language, title, includeName), PPTX);
    }

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    static ResponseEntity<ByteArrayResource> download(ManagementReportService.ReportFile file, String mediaType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(new ByteArrayResource(file.content()));
    }
}
