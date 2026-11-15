package com.visionmapping.controller;

import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.service.ManagementReportService;
import com.visionmapping.util.UserScope;
import com.visionmapping.service.support.RequestRateLimiter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** MR-FR-03, MR-FR-07 to MR-FR-13: the Summary for all dreams, one vision area or one dream. */
    @GetMapping("/management")
    public ManagementReportResponse summary(@RequestParam(required = false) Long areaId,
                                            @RequestParam(required = false) Long dreamId) {
        return service.summary(areaId, dreamId);
    }

    /** MR-FR-15 to MR-FR-22: the Excel report. Nothing is written to disk. */
    @GetMapping("/management.xlsx")
    public ResponseEntity<ByteArrayResource> excel(@RequestParam(required = false) Long areaId,
                                                   @RequestParam(required = false) Long dreamId,
                                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_EXPORT, "user:" + userScope.currentUser().getId());
        return download(service.excel(areaId, dreamId, language),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /** MR-FR-23 to MR-FR-31: the PowerPoint deck for a management review. */
    @GetMapping("/management.pptx")
    public ResponseEntity<ByteArrayResource> deck(@RequestParam(required = false) Long areaId,
                                                  @RequestParam(required = false) Long dreamId,
                                                  @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                                  @RequestParam(required = false) @Size(max = 80) String title,
                                                  @RequestParam(defaultValue = "true") boolean includeName) {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_EXPORT, "user:" + userScope.currentUser().getId());
        return download(service.deck(areaId, dreamId, language, title, includeName),
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    private static ResponseEntity<ByteArrayResource> download(ManagementReportService.ReportFile file, String mediaType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(new ByteArrayResource(file.content()));
    }
}
