package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;
import java.util.Map;

public record ExcelImportSummaryResponse(
        int createdRecords,
        int skippedRecords,
        Map<String, Integer> rowsBySheet,
        List<String> validationErrors,
        // Path of the automatic export snapshot saved before the import ran
        // (BRD C-7); null when the import never started.
        String backupFile
) {

    public ExcelImportSummaryResponse {
        rowsBySheet = Copies.map(rowsBySheet);
        validationErrors = Copies.list(validationErrors);
    }


    public ExcelImportSummaryResponse(int createdRecords, int skippedRecords, Map<String, Integer> rowsBySheet, List<String> validationErrors) {
        this(createdRecords, skippedRecords, rowsBySheet, validationErrors, null);
    }

    public ExcelImportSummaryResponse withBackupFile(String file) {
        return new ExcelImportSummaryResponse(createdRecords, skippedRecords, rowsBySheet, validationErrors, file);
    }
}
