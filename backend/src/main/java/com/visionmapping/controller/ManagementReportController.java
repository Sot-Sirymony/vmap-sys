package com.visionmapping.controller;

import com.visionmapping.dto.request.ReportQuery;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ReportRowsResponse;
import com.visionmapping.mapper.ReportRowsMapper;
import com.visionmapping.service.ManagementReportService;
import com.visionmapping.service.ReportActionsService;
import com.visionmapping.service.report.ReportSection;
import com.visionmapping.web.FileDownloads;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * V18 (BRD 18.0.0): the Management Report under the Vision Map. Read-only and
 * scoped to the signed-in participant. V33 (D-33-11): the rate limits and the
 * compare-or-not choice are in {@link ReportActionsService}.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/reports", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Management report", description = "The live Management Report and its downloads")
public class ManagementReportController {

    private final ManagementReportService service;
    private final ReportActionsService actions;

    @Operation(summary = "Get the management report summary, compared with a final report when asked")
    @GetMapping("/management")
    public ResponseEntity<ManagementReportResponse> summary(@Valid @ModelAttribute ReportQuery query) {
        return ResponseEntity.ok(actions.summary(query));
    }

    @Operation(summary = "Get the management report's rows")
    @GetMapping("/management/rows")
    public ResponseEntity<ReportRowsResponse> rows(@Valid @ModelAttribute ReportQuery query,
                                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(ReportRowsMapper.toResponse(service.rows(service.build(query.toScope()), language)));
    }

    @Operation(summary = "Download the management report as Excel")
    @GetMapping(value = "/management.xlsx", produces = FileDownloads.XLSX)
    public ResponseEntity<ByteArrayResource> excel(@Valid @ModelAttribute ReportQuery query,
                                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                                   @RequestParam(required = false) List<ReportSection> sections) {
        return FileDownloads.attachment(actions.excel(query, language, sections), FileDownloads.XLSX);
    }

    @Operation(summary = "Download the management report as slides")
    @GetMapping(value = "/management.pptx", produces = FileDownloads.PPTX)
    public ResponseEntity<ByteArrayResource> deck(@Valid @ModelAttribute ReportQuery query,
                                                  @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                                  @RequestParam(required = false) @Size(max = 80) String title,
                                                  @RequestParam(defaultValue = "true") boolean includeName,
                                                  @RequestParam(required = false) List<ReportSection> sections) {
        return FileDownloads.attachment(actions.deck(query, language, title, includeName, sections), FileDownloads.PPTX);
    }

    @Operation(summary = "Download the management report as a PDF")
    @GetMapping(value = "/management.pdf", produces = FileDownloads.PDF)
    public ResponseEntity<ByteArrayResource> pdf(@Valid @ModelAttribute ReportQuery query,
                                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                                 @RequestParam(required = false) List<ReportSection> sections) {
        return FileDownloads.attachment(actions.pdf(query, language, sections), FileDownloads.PDF);
    }
}
