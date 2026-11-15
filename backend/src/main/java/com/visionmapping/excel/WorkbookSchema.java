package com.visionmapping.excel;

/**
 * Single source of truth for the sheet names and column labels shared by the
 * Excel export ({@link ExcelService}) and import ({@link HierarchyImport}).
 * Keeping the workbook's vocabulary here stops the two sides from drifting and
 * removes the repeated string literals each side used to carry.
 */
final class WorkbookSchema {

    private WorkbookSchema() {
    }

    // Sheet names
    static final String VISION_AREAS = "Vision Areas";
    static final String DREAMS = "Dreams";
    static final String GOALS = "Goals";
    static final String STEPS = "Steps";
    static final String TASKS = "Tasks";
    static final String OBSTACLES = "Obstacles";

    // V17 BL-FR-40: the Obstacles sheet's last column, written by export only.
    static final String PLACED_ON = "Placed on";
    static final int PLACED_ON_COLUMN = 10;
    static final String NOT_PLACED = "Not placed";

    // Column labels reused across sheets
    static final String CODE = "Code";
    static final String TITLE = "Title";
    static final String TYPE = "Type";
    static final String PRIORITY = "Priority";
    static final String STATUS = "Status";
}
