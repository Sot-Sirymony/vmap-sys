package com.visionmapping.controller;

import com.visionmapping.util.UserScope;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.dto.response.ExcelImportSummaryResponse;
import com.visionmapping.excel.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelService excelService;
    private final RequestRateLimiter rateLimiter;
    private final UserScope userScope;

    @PostMapping("/export")
    public ResponseEntity<ByteArrayResource> exportWorkbook() {
        byte[] content = excelService.exportWorkbook();
        ByteArrayResource resource = new ByteArrayResource(content);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("vision-mapping-export.xlsx")
                        .build()
                        .toString())
                .body(resource);
    }

    @PostMapping("/import")
    public ExcelImportSummaryResponse importWorkbook(@RequestParam("file") MultipartFile file) {
        // V10 (SEC-06-02): each import parses a whole workbook, so it is limited per account.
        rateLimiter.check(RequestRateLimiter.Action.EXCEL_IMPORT, "user:" + userScope.currentUser().getId());
        return excelService.importWorkbook(file);
    }
}
