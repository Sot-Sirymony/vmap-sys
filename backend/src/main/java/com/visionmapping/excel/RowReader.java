package com.visionmapping.excel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.lang.Nullable;

/**
 * Typed, forgiving access to one spreadsheet row. Wraps Apache POI so the
 * import logic never touches the library directly, and turns bad or missing
 * cells into {@link RowParseException} with a human-readable reason.
 * Columns are 1-based in messages (what a user sees) but 0-based in calls.
 */
class RowReader {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private final Row row;

    RowReader(Row row) {
        this.row = row;
    }

    /** The spreadsheet's own row number (the header is row 1). */
    int rowNumber() {
        return row.getRowNum() + 1;
    }

    boolean isBlank() {
        for (int column = 0; column < row.getLastCellNum(); column++) {
            if (!text(column).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    String text(int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return cachedValue(cell).trim();
        }
        return FORMATTER.formatCellValue(cell).trim();
    }

    /**
     * V10 (SEC-05-05): a formula is never evaluated (it could point at other
     * files or sites). The value the spreadsheet last showed is used instead,
     * as plain data.
     */
    private static String cachedValue(Cell cell) {
        return switch (cell.getCachedFormulaResultType()) {
            case STRING -> cell.getRichStringCellValue().getString();
            case NUMERIC -> FORMATTER.formatRawCellContents(cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString());
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            default -> "";
        };
    }

    @Nullable
    String textOrNull(int column) {
        String value = text(column);
        return value.isEmpty() ? null : value;
    }

    String requiredText(int column, String field) {
        String value = text(column);
        if (value.isEmpty()) {
            throw new RowParseException(field + " is required");
        }
        return value;
    }

    /**
     * A link to another row: its ID (an exported database id) or its Code
     * (such as {@code G-002}). Numbers are written without decimals and codes
     * in capitals, so "406", "406.0" and 406 match, and so do "g-002" and "G-002".
     */
    @Nullable
    String keyOrNull(int column) {
        String value = text(column);
        return value.isEmpty() ? null : key(value);
    }

    String requiredKey(int column, String field) {
        return key(requiredText(column, field));
    }

    static String key(String value) {
        String trimmed = value.trim();
        try {
            double number = Double.parseDouble(trimmed);
            if (number == Math.rint(number) && !Double.isInfinite(number)) {
                return Long.toString((long) number);
            }
        } catch (NumberFormatException exception) {
            // Not a number: a code such as G-002.
        }
        return trimmed.toUpperCase(java.util.Locale.ROOT);
    }

    @Nullable
    LocalDate dateOrNull(int column) {
        String value = text(column);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new RowParseException("invalid date '" + value + "' in column " + (column + 1));
        }
    }

    LocalDate requiredDate(int column, String field) {
        LocalDate value = dateOrNull(column);
        if (value == null) {
            throw new RowParseException(field + " is required");
        }
        return value;
    }

    BigDecimal decimalOrDefault(int column, BigDecimal fallback) {
        String value = text(column);
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new RowParseException("invalid number '" + value + "' in column " + (column + 1));
        }
    }

    int intOrDefault(int column, int fallback) {
        String value = text(column);
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new RowParseException("invalid whole number '" + value + "' in column " + (column + 1));
        }
    }

    boolean bool(int column) {
        return Boolean.parseBoolean(text(column));
    }

    <E extends Enum<E>> E enumValue(Class<E> type, int column, String field) {
        String value = requiredText(column, field).toUpperCase();
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new RowParseException("invalid " + field + " '" + value + "'");
        }
    }
}
