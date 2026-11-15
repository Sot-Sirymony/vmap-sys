package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;
import java.util.Map;

public record ExcelImportSummaryResponse(
        int createdRecords,
        int skippedRecords,
        Map<String, Integer> rowsBySheet,
        List<String> validationErrors,
        // V17 BL-FR-41: rows that were imported but need attention, such as
        // obstacles that are not on a step yet. Never a reason to skip a row.
        List<String> warnings,
        // Path of the automatic export snapshot saved before the import ran
        // (BRD C-7); null when the import never started.
        String backupFile
) {

    public ExcelImportSummaryResponse {
        rowsBySheet = Copies.map(rowsBySheet);
        validationErrors = Copies.list(validationErrors);
        warnings = Copies.list(warnings);
    }

    public ExcelImportSummaryResponse(int createdRecords, int skippedRecords, Map<String, Integer> rowsBySheet, List<String> validationErrors) {
        this(createdRecords, skippedRecords, rowsBySheet, validationErrors, List.of(), null);
    }

    public ExcelImportSummaryResponse(int createdRecords, int skippedRecords, Map<String, Integer> rowsBySheet,
                                      List<String> validationErrors, List<String> warnings) {
        this(createdRecords, skippedRecords, rowsBySheet, validationErrors, warnings, null);
    }

    public ExcelImportSummaryResponse withBackupFile(String file) {
        return new ExcelImportSummaryResponse(createdRecords, skippedRecords, rowsBySheet, validationErrors, warnings, file);
    }
}
