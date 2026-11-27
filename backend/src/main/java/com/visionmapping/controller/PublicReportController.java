package com.visionmapping.controller;

import com.visionmapping.dto.response.SharedReportResponse;
import com.visionmapping.service.ReportActionsService;
import com.visionmapping.web.FileDownloads;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * V19 (BRD 19.0.0, RM-FR-47): a shared final report, opened without signing
 * in. The 256-bit token in the path is the key; every failure is the same
 * 404. Answers tell search engines not to index them and browsers not to send
 * the address on (no referrer), and are not cached. Views are limited to 60 a
 * minute per link (RM-NFR-07).
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/public/reports", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Public reports", description = "A shared final report, opened through its link without signing in")
public class PublicReportController {

    static final String ROBOTS = "X-Robots-Tag";
    static final String NO_INDEX = "noindex, nofollow";
    static final String REFERRER_POLICY = "Referrer-Policy";
    static final String NO_REFERRER = "no-referrer";

    private final ReportActionsService reports;

    @Operation(summary = "Open a shared report through its link")
    @GetMapping("/{token}")
    public ResponseEntity<SharedReportResponse> view(@PathVariable @Size(max = 200) String token) {
        return ResponseEntity.ok()
                .header(ROBOTS, NO_INDEX)
                .header(REFERRER_POLICY, NO_REFERRER)
                .cacheControl(CacheControl.noStore())
                .body(reports.publicView(token));
    }

    @Operation(summary = "Download a shared report as a PDF")
    @GetMapping(value = "/{token}.pdf", produces = FileDownloads.PDF)
    public ResponseEntity<ByteArrayResource> pdf(@PathVariable @Size(max = 200) String token,
                                                 @RequestParam(required = false) @Pattern(regexp = "en|km") String language) {
        ResponseEntity<ByteArrayResource> file = FileDownloads.attachment(reports.publicPdf(token, language), FileDownloads.PDF);
        return ResponseEntity.status(file.getStatusCode())
                .headers(file.getHeaders())
                .header(ROBOTS, NO_INDEX)
                .header(REFERRER_POLICY, NO_REFERRER)
                .cacheControl(CacheControl.noStore())
                .body(file.getBody());
    }
}
