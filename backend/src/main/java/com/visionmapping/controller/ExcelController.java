package com.visionmapping.controller;

import com.visionmapping.dto.response.ExcelImportSummaryResponse;
import com.visionmapping.excel.ExcelService;
import com.visionmapping.web.FileDownloads;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/** Excel import and export of the whole plan (CLAUDE.md Excel Import / Export). */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/excel", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Excel", description = "Import and export the plan as an Excel workbook")
public class ExcelController {

    private final ExcelService excelService;

    @Operation(summary = "Export my plan as an Excel workbook")
    @PostMapping(value = "/export", produces = FileDownloads.XLSX)
    public ResponseEntity<ByteArrayResource> exportWorkbook() {
        return FileDownloads.attachment("vision-mapping-export.xlsx", excelService.exportWorkbook(), FileDownloads.XLSX);
    }

    /** V10 (SEC-06-02): each import parses a whole workbook, so it is limited per account. */
    @Operation(summary = "Import a plan from an Excel workbook")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExcelImportSummaryResponse> importWorkbook(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(excelService.importWorkbookLimited(file));
    }
}
