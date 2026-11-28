package com.visionmapping.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SheetColumnsTest {

    @Test
    void fitsEachColumnToItsLongestValue() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("Title");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(7);
            row.createCell(1).setCellValue("Prepare one concept note");

            SheetColumns.fitAll(sheet, 2);

            assertThat(sheet.getColumnWidth(0)).isEqualTo((4 + 2) * 256);
            assertThat(sheet.getColumnWidth(1)).isEqualTo(("Prepare one concept note".length() + 2) * 256);
        }
    }

    @Test
    void measuresFormattedDatesAndTheLongestLineAndSkipsEmptyRows() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("d mmm yyyy"));
            Row first = sheet.createRow(0);
            first.createCell(0).setCellValue(LocalDate.of(2026, 11, 28));
            first.getCell(0).setCellStyle(dateStyle);
            first.createCell(1).setCellValue("short\na much longer second line");
            sheet.createRow(3);

            SheetColumns.fitAll(sheet, 2);

            assertThat(sheet.getColumnWidth(0)).isEqualTo(("28 Nov 2026".length() + 2) * 256);
            assertThat(sheet.getColumnWidth(1)).isEqualTo(("a much longer second line".length() + 2) * 256);
        }
    }

    @Test
    void neverGoesPastExcelsLimit() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("x".repeat(400));

            SheetColumns.fit(sheet, 0);

            assertThat(sheet.getColumnWidth(0)).isEqualTo(255 * 256);
        }
    }

    /** autoSizeColumn needs system fonts, which the production image has none of: every Excel download failed. */
    @Test
    void noMainCodeCallsAutoSizeColumn() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<Path> offenders = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !file.endsWith("SheetColumns.java"))
                    .filter(file -> read(file).contains(".autoSizeColumn("))
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
