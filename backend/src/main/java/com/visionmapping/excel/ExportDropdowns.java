package com.visionmapping.excel;

import static com.visionmapping.excel.WorkbookSchema.DREAMS;
import static com.visionmapping.excel.WorkbookSchema.GOALS;
import static com.visionmapping.excel.WorkbookSchema.OBSTACLES;
import static com.visionmapping.excel.WorkbookSchema.PRIORITY;
import static com.visionmapping.excel.WorkbookSchema.STATUS;
import static com.visionmapping.excel.WorkbookSchema.STEPS;
import static com.visionmapping.excel.WorkbookSchema.TASKS;
import static com.visionmapping.excel.WorkbookSchema.TYPE;
import static com.visionmapping.excel.WorkbookSchema.VISION_AREAS;

import com.visionmapping.entity.enums.CommunicationStatus;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.EnergyDemand;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.OfferType;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.util.Copies;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * Dropdowns on every export column that holds a fixed list of values, so the
 * workbook works as a template: a new row picks its Priority, Status, Type and
 * so on from a list instead of typing it. Each list is read from the app's own
 * enum, so it always matches what the import accepts. Columns are found by
 * their header, so moving a column never puts a list on the wrong one.
 */
final class ExportDropdowns {

    /** Rows below the header that get the dropdown: room for a plan to grow. */
    static final int ROWS = 1_000;

    private static final List<String> TRUE_FALSE = List.of("true", "false");

    record Rule(String sheet, String header, List<String> values) {
        Rule {
            values = Copies.list(values);
        }
    }

    static final List<Rule> RULES = List.of(
            rule(VISION_AREAS, PRIORITY, Priority.class),
            rule(VISION_AREAS, STATUS, LifecycleStatus.class),
            rule(DREAMS, TYPE, DreamType.class),
            rule(DREAMS, PRIORITY, Priority.class),
            rule(DREAMS, STATUS, DreamStatus.class),
            rule(GOALS, PRIORITY, Priority.class),
            rule(GOALS, STATUS, WorkStatus.class),
            new Rule(STEPS, "Complex", TRUE_FALSE),
            rule(STEPS, PRIORITY, Priority.class),
            rule(STEPS, STATUS, WorkStatus.class),
            rule(TASKS, PRIORITY, Priority.class),
            rule(TASKS, STATUS, WorkStatus.class),
            rule(TASKS, "Energy", EnergyDemand.class),
            rule("Partners", "Support Type", PartnerSupportType.class),
            rule("Partners", "Offer Type", OfferType.class),
            rule("Partners", STATUS, PartnerStatus.class),
            rule("Communication", STATUS, CommunicationStatus.class),
            rule("Reviews", TYPE, ReviewType.class),
            rule(OBSTACLES, TYPE, ObstacleType.class),
            rule(OBSTACLES, "Severity", Severity.class),
            rule(OBSTACLES, STATUS, ObstacleStatus.class));

    private ExportDropdowns() {
    }

    private static Rule rule(String sheet, String header, Class<? extends Enum<?>> type) {
        return new Rule(sheet, header, Arrays.stream(type.getEnumConstants()).map(Enum::name).toList());
    }

    static void addAll(Workbook workbook) {
        for (Rule rule : RULES) {
            Sheet sheet = workbook.getSheet(rule.sheet());
            int column = sheet == null ? -1 : column(sheet, rule.header());
            if (column >= 0) {
                add((XSSFSheet) sheet, column, rule.values());
            }
        }
    }

    private static int column(Sheet sheet, String header) {
        Row headers = sheet.getRow(0);
        if (headers == null) {
            return -1;
        }
        for (Cell cell : headers) {
            if (header.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private static void add(XSSFSheet sheet, int column, List<String> values) {
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper(sheet);
        DataValidation validation = helper.createValidation(
                helper.createExplicitListConstraint(values.toArray(String[]::new)),
                new CellRangeAddressList(1, ROWS, column, column));
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("Choose from the list", "Pick one of: " + String.join(", ", values));
        validation.setEmptyCellAllowed(true);
        sheet.addValidationData(validation);
    }
}
