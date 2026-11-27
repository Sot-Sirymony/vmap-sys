package com.visionmapping.web;

import com.visionmapping.service.ManagementReportService.ReportFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** V33 (D-33-4): the one way a controller answers with a file to download. */
public final class FileDownloads {

    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    public static final String PDF = "application/pdf";

    private FileDownloads() {
    }

    /** An attachment with its name, type and length. */
    public static ResponseEntity<ByteArrayResource> attachment(String fileName, byte[] content, String mediaType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(new ByteArrayResource(content));
    }

    /** A report file as an attachment. */
    public static ResponseEntity<ByteArrayResource> attachment(ReportFile file, String mediaType) {
        return attachment(file.fileName(), file.content(), mediaType);
    }
}
