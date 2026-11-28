package com.visionmapping.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * Sets a column's width from the text it holds, counted in characters.
 *
 * <p>POI's {@code Sheet.autoSizeColumn} measures text with Java's font engine,
 * which asks the operating system for a font. The production image (Alpine)
 * has no system fonts, so that call failed and every Excel download answered
 * 500. Counting characters needs no fonts, gives the same result on every
 * machine, and is close enough for the reading widths these sheets need.
 */
public final class SheetColumns {

    /** Excel's own limit: 255 characters, in 1/256ths of a character. */
    private static final int MAX_WIDTH = 255 * 256;
    /** A little room either side of the longest value. */
    private static final int PADDING_CHARS = 2;
    private static final int MIN_CHARS = 4;

    private SheetColumns() {
    }

    /** Fits one column to its longest value (the longest line, for text with line breaks). */
    public static void fit(Sheet sheet, int column) {
        DataFormatter formatter = new DataFormatter();
        int longest = 0;
        for (Row row : sheet) {
            Cell cell = row.getCell(column);
            if (cell != null) {
                longest = Math.max(longest, longestLine(formatter.formatCellValue(cell)));
            }
        }
        int chars = Math.max(MIN_CHARS, longest) + PADDING_CHARS;
        sheet.setColumnWidth(column, Math.min(MAX_WIDTH, chars * 256));
    }

    /** Fits the first {@code count} columns. */
    public static void fitAll(Sheet sheet, int count) {
        for (int column = 0; column < count; column++) {
            fit(sheet, column);
        }
    }

    static int longestLine(String text) {
        int longest = 0;
        for (String line : text.split("\n", -1)) {
            longest = Math.max(longest, line.length());
        }
        return longest;
    }
}
