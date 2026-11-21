package com.visionmapping.controller;

import com.visionmapping.dto.response.SharedReportResponse;
import com.visionmapping.service.ReportShareService;
import com.visionmapping.service.support.RequestRateLimiter;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V19 (BRD 19.0.0, RM-FR-47): a shared final report, opened without signing
 * in. The 256-bit token in the path is the key; every failure is the same
 * 404. Answers tell search engines not to index them and browsers not to send
 * the address on (no referrer), and are not cached. Views are limited to 60 a
 * minute per link (RM-NFR-07).
 */
@RestController
@Validated
@RequestMapping("/api/public/reports")
@RequiredArgsConstructor
public class PublicReportController {

    static final String ROBOTS = "X-Robots-Tag";
    static final String NO_INDEX = "noindex, nofollow";

    private final ReportShareService service;
    private final RequestRateLimiter rateLimiter;

    @GetMapping("/{token}")
    public ResponseEntity<SharedReportResponse> view(@PathVariable String token) {
        rateLimiter.check(RequestRateLimiter.Action.SHARED_REPORT, ReportShareService.hash(token));
        return ResponseEntity.ok()
                .header(ROBOTS, NO_INDEX)
                .header("Referrer-Policy", "no-referrer")
                .cacheControl(CacheControl.noStore())
                .body(service.view(token));
    }

    @GetMapping("/{token}.pdf")
    public ResponseEntity<ByteArrayResource> pdf(@PathVariable String token,
                                                 @RequestParam(required = false) @Pattern(regexp = "en|km") String language) {
        rateLimiter.check(RequestRateLimiter.Action.SHARED_REPORT, ReportShareService.hash(token));
        ResponseEntity<ByteArrayResource> file = ManagementReportController.download(service.pdf(token, language), ManagementReportController.PDF);
        return ResponseEntity.status(file.getStatusCode())
                .headers(file.getHeaders())
                .header(ROBOTS, NO_INDEX)
                .header("Referrer-Policy", "no-referrer")
                .cacheControl(CacheControl.noStore())
                .body(file.getBody());
    }
}
