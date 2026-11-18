package com.visionmapping.service.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.exception.BusinessRuleException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * V19 RM-FR-19, RM-NFR-04, RM-NFR-06: a final report's frozen content. The
 * model (for the files) and the Summary as shown on the day it was finalised
 * are stored as JSON, gzip-compressed and base64-encoded, with a SHA-256 of
 * the JSON so tampering or corruption is noticed on read.
 *
 * <p>Each stored snapshot keeps the version it was written with. A later
 * change to the model adds a new version and a reader for it here; old
 * versions stay readable (RMC-21).
 */
public final class ReportSnapshotCodec {

    public static final int CURRENT_VERSION = 1;
    /** RM-NFR-06: the most a snapshot may take, compressed and encoded. */
    public static final int MAX_STORED_CHARS = 5 * 1024 * 1024;
    static final String TOO_LARGE = "This report is too large to finalise. Choose one vision area or one dream.";
    static final String UNKNOWN_VERSION = "This report was saved by a newer version of the app.";

    /** The frozen content: the model and the Summary. */
    public record Snapshot(ManagementReport report, ManagementReportResponse summary) {
    }

    /** What is stored: base64 text and the checksum of the JSON inside. */
    public record Encoded(String data, String checksum, int version) {
    }

    /** A read snapshot and whether its checksum matched. */
    public record Decoded(Snapshot snapshot, boolean verified) {
    }

    private final ObjectMapper mapper;

    public ReportSnapshotCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Encoded encode(Snapshot snapshot) {
        try {
            byte[] json = mapper.writeValueAsBytes(snapshot);
            String data = Base64.getEncoder().encodeToString(gzip(json));
            if (data.length() > MAX_STORED_CHARS) {
                throw new BusinessRuleException(TOO_LARGE);
            }
            return new Encoded(data, sha256(json), CURRENT_VERSION);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to store the report snapshot.", exception);
        }
    }

    public Decoded decode(String data, String checksum, int version) {
        if (version != CURRENT_VERSION) {
            throw new BusinessRuleException(UNKNOWN_VERSION);
        }
        try {
            byte[] json = gunzip(Base64.getDecoder().decode(data));
            Snapshot snapshot = mapper.readValue(json, Snapshot.class);
            return new Decoded(snapshot, sha256(json).equals(checksum));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read the report snapshot.", exception);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(bytes);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return zip.readAllBytes();
        }
    }

    /** For tests and diagnostics: the JSON a snapshot is stored as. */
    String json(Snapshot snapshot) throws IOException {
        return new String(mapper.writeValueAsBytes(snapshot), StandardCharsets.UTF_8);
    }
}
