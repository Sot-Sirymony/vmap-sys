package com.visionmapping.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExportDropdownsTest {

    @Test
    void everyRuleFindsItsColumnByHeaderAndListsEveryValue() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Each header sits one column further right than the last, so a list on the wrong column shows.
            for (ExportDropdowns.Rule rule : ExportDropdowns.RULES) {
                XSSFSheet sheet = workbook.getSheet(rule.sheet()) != null ? workbook.getSheet(rule.sheet()) : workbook.createSheet(rule.sheet());
                Row header = sheet.getRow(0) != null ? sheet.getRow(0) : sheet.createRow(0);
                header.createCell(Math.max(0, header.getLastCellNum()) + 1).setCellValue(rule.header());
            }

            ExportDropdowns.addAll(workbook);

            for (ExportDropdowns.Rule rule : ExportDropdowns.RULES) {
                XSSFSheet sheet = workbook.getSheet(rule.sheet());
                int column = columnOf(sheet, rule.header());
                DataValidation validation = sheet.getDataValidations().stream()
                        .filter(candidate -> covers(candidate, column))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no dropdown on " + rule.sheet() + " / " + rule.header()));
                assertThat(List.of(validation.getValidationConstraint().getExplicitListValues()))
                        .as(rule.sheet() + " / " + rule.header())
                        .isEqualTo(rule.values());
                assertThat(validation.getErrorStyle()).isEqualTo(DataValidation.ErrorStyle.STOP);
            }
        }
    }

    @Test
    void listsComeFromTheEnumsTheImportReads() {
        ExportDropdowns.Rule priority = ExportDropdowns.RULES.stream()
                .filter(rule -> rule.sheet().equals(WorkbookSchema.TASKS) && rule.header().equals(WorkbookSchema.PRIORITY))
                .findFirst().orElseThrow();
        assertThat(priority.values()).containsExactly("LOW", "MEDIUM", "HIGH", "CRITICAL");
    }

    @Test
    void aMissingSheetOrHeaderIsSkipped() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet(WorkbookSchema.TASKS).createRow(0).createCell(0).setCellValue("Something else");
            workbook.createSheet(WorkbookSchema.GOALS);

            ExportDropdowns.addAll(workbook);

            assertThat(workbook.getSheet(WorkbookSchema.TASKS).getDataValidations()).isEmpty();
            assertThat(workbook.getSheet(WorkbookSchema.GOALS).getDataValidations()).isEmpty();
        }
    }

    private static int columnOf(XSSFSheet sheet, String header) {
        for (var cell : sheet.getRow(0)) {
            if (header.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError(header);
    }

    private static boolean covers(DataValidation validation, int column) {
        for (CellRangeAddress range : validation.getRegions().getCellRangeAddresses()) {
            if (range.getFirstColumn() == column && range.getLastColumn() == column
                    && range.getFirstRow() == 1 && range.getLastRow() == ExportDropdowns.ROWS) {
                return true;
            }
        }
        return false;
    }
}
