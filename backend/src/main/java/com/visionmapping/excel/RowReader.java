package com.visionmapping.excel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

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

    Long referenceOrNull(int column) {
        String value = text(column);
        return value.isEmpty() ? null : parseReference(value, "reference in column " + (column + 1));
    }

    long requiredReference(int column, String field) {
        return parseReference(requiredText(column, field), field);
    }

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

    private long parseReference(String value, String field) {
        try {
            return (long) Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new RowParseException(field + " must be a number");
        }
    }
}
