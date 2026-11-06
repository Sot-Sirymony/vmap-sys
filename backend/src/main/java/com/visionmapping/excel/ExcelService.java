package com.visionmapping.excel;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import lombok.extern.slf4j.Slf4j;
import java.util.Locale;
import jakarta.validation.Validator;
import static com.visionmapping.excel.WorkbookSchema.CODE;
import static com.visionmapping.excel.WorkbookSchema.DREAMS;
import static com.visionmapping.excel.WorkbookSchema.GOALS;
import static com.visionmapping.excel.WorkbookSchema.PRIORITY;
import static com.visionmapping.excel.WorkbookSchema.STATUS;
import static com.visionmapping.excel.WorkbookSchema.STEPS;
import static com.visionmapping.excel.WorkbookSchema.TASKS;
import static com.visionmapping.excel.WorkbookSchema.TITLE;
import static com.visionmapping.excel.WorkbookSchema.TYPE;
import static com.visionmapping.excel.WorkbookSchema.VISION_AREAS;

import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.dto.response.ExcelImportSummaryResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.dto.response.PartnerResponse;
import com.visionmapping.dto.response.ProgressLogResponse;
import com.visionmapping.dto.response.ReviewResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.service.ProgressLogService;
import com.visionmapping.service.CommunicationMessageService;
import com.visionmapping.service.PartnerService;
import com.visionmapping.service.TaskItemService;
import com.visionmapping.service.DreamService;
import com.visionmapping.service.VisionAreaService;
import com.visionmapping.service.GoalService;
import com.visionmapping.service.VisionStepService;
import com.visionmapping.service.ObstacleService;
import com.visionmapping.service.ReviewService;
import com.visionmapping.service.DashboardService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelService {

    /** V10 (SEC-08-02): limits on what an import may contain. */
    static final int MAX_SHEETS = 30;
    static final int MAX_ROWS_PER_SHEET = 5_000;
    /** Largest single part of the .xlsx once unzipped; a zip bomb is refused before it fills the heap. */
    static final long MAX_UNZIPPED_ENTRY_BYTES = 50L * 1024 * 1024;
    /** Characters that make a spreadsheet treat a typed value as a formula (SEC-05-04). */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    static {
        // POI checks every zip entry as it is read: one that inflates more than
        // 100 times (ratio 0.01) or past the size limit stops the read.
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(MAX_UNZIPPED_ENTRY_BYTES);
    }

    private static final List<String> REQUIRED_SHEETS = List.of(
            "Dashboard",
            VISION_AREAS,
            DREAMS,
            GOALS,
            STEPS,
            TASKS,
            "Partners",
            "Communication",
            "Reviews",
            "Obstacles",
            "Progress Logs",
            "Instructions"
    );

    // Where pre-import snapshots land (BRD C-7). Not final so Lombok's
    // required-args constructor keeps covering only the service dependencies.
    @Value("${app.excel.backup-dir:data/backup}")
    private String backupDirectory;

    private final DashboardService dashboardService;
    private final ProgressLogService progressLogService;
    private final ReviewService reviewService;
    private final ObstacleService obstacleService;
    private final CommunicationMessageService communicationMessageService;
    private final PartnerService partnerService;
    private final TaskItemService taskItemService;
    private final VisionStepService visionStepService;
    private final GoalService goalService;
    private final DreamService dreamService;
    private final VisionAreaService visionAreaService;
    private final Validator validator;

    public byte[] exportWorkbook() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);

            DashboardSummaryResponse dashboard = dashboardService.buildDashboardSummary();
            writeRows(workbook, "Dashboard", List.of("Metric", "Value"), List.of(
                    List.of("Total Vision Areas", dashboard.totalVisionAreas()),
                    List.of("Active Dreams", dashboard.activeDreams()),
                    List.of("Active Goals", dashboard.activeGoals()),
                    List.of("Active Tasks", dashboard.activeTasks()),
                    List.of("Completed Tasks", dashboard.completedTasks()),
                    List.of("Overdue Tasks", dashboard.overdueTasks()),
                    List.of("Blocked Tasks", dashboard.blockedTasks()),
                    List.of("Average Progress", dashboard.averageProgress()),
                    List.of("Tasks Due This Week", dashboard.tasksDueThisWeek())
            ), headerStyle);

            writeRows(workbook, VISION_AREAS, List.of("ID", CODE, "Name", "Description", PRIORITY, STATUS),
                    visionAreaService.listVisionAreas(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.name(), value(item.description()), item.priority(), item.status()))
                            .toList(), headerStyle);

            writeRows(workbook, DREAMS, List.of("ID", CODE, "Vision Area ID", TITLE, "Why Important", "Success Definition", TYPE, PRIORITY, "Target Date", STATUS),
                    dreamService.listDreams(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.visionAreaId(), item.title(), value(item.whyImportant()), value(item.successDefinition()), item.dreamType(), item.priority(), value(item.targetDate()), item.status()))
                            .toList(), headerStyle);

            writeRows(workbook, GOALS, List.of("ID", CODE, "Dream ID", TITLE, "Success Criteria", PRIORITY, "Target Date", STATUS, "Progress"),
                    goalService.listGoals(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.dreamId(), item.title(), value(item.successCriteria()), item.priority(), value(item.targetDate()), item.status(), item.progressPercent()))
                            .toList(), headerStyle);

            writeRows(workbook, STEPS, List.of("ID", CODE, "Goal ID", TITLE, "Sequence", "Complex", PRIORITY, "Target Date", STATUS, "Progress", "Owner"),
                    visionStepService.listSteps(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.goalId(), item.title(), item.sequenceNumber(), item.complex(), item.priority(), value(item.targetDate()), item.status(), item.progressPercent(), value(item.owner())))
                            .toList(), headerStyle);

            writeRows(workbook, TASKS, List.of("ID", CODE, "Step ID", TITLE, "Owner", PRIORITY, "Start Date", "Due Date", STATUS, "Progress", "Blocker", "Next Action", "Energy"),
                    taskItemService.listTasks(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.stepId(), item.title(), item.owner(), item.priority(), value(item.startDate()), item.dueDate(), item.status(), item.progressPercent(), value(item.blockerReason()), value(item.nextAction()), value(item.energyDemand())))
                            .toList(), headerStyle);

            writeRows(workbook, "Partners", List.of("ID", CODE, "Name", "Role", "Organization", "Email", "Support Type", "Offer Type", STATUS, "Notes"),
                    partnerService.listPartners(Pageable.unpaged(), false, null).stream()
                            .map(item -> List.of(item.id(), item.code(), item.name(), value(item.role()), value(item.organization()), value(item.email()), item.supportType(), value(item.offerType()), item.status(), value(item.notes())))
                            .toList(), headerStyle);

            writeRows(workbook, "Communication", List.of("ID", "Partner ID", "Audience", "Purpose", "Subject", "Request", "Message", STATUS, "Follow Up"),
                    communicationMessageService.listCommunicationMessages(Pageable.unpaged(), false, null).stream()
                            .map(item -> List.of(item.id(), value(item.partnerId()), value(item.audience()), value(item.purpose()), value(item.subject()), value(item.request()), value(item.messageBody()), item.status(), value(item.followUpDate())))
                            .toList(), headerStyle);

            writeRows(workbook, "Reviews", List.of("ID", TYPE, "Date", "Vision Area ID", "Dream ID", "Summary", "Next Actions"),
                    reviewService.listReviews(false).stream()
                            .map(item -> List.of(item.id(), item.reviewType(), item.reviewDate(), value(item.relatedVisionAreaId()), value(item.relatedDreamId()), value(item.summary()), value(item.nextActions())))
                            .toList(), headerStyle);

            writeRows(workbook, "Obstacles", List.of("ID", "Dream ID", "Goal ID", "Step ID", "Task ID", TITLE, TYPE, "Severity", STATUS, "Solution"),
                    obstacleService.listObstacles(false).stream()
                            .map(item -> List.of(item.id(), value(item.relatedDreamId()), value(item.relatedGoalId()), value(item.relatedStepId()), value(item.relatedTaskId()), item.title(), item.obstacleType(), item.severity(), item.status(), value(item.solution())))
                            .toList(), headerStyle);

            writeRows(workbook, "Progress Logs", List.of("ID", "Task ID", "Before", "After", "Note", "Logged At"),
                    progressLogService.listProgressLogs().stream()
                            .map(item -> List.of(item.id(), item.relatedTaskId(), item.progressPercentBefore(), item.progressPercentAfter(), value(item.note()), item.loggedAt()))
                            .toList(), headerStyle);

            writeRows(workbook, "Instructions", List.of("Topic", "Instruction"), List.of(
                    List.of("Import", "Use this workbook as a clean template. Keep required sheet names and header rows."),
                    List.of("Export", "Export creates a new workbook and does not overwrite the original workbook."),
                    List.of(STATUS, "Use application values such as ACTIVE, IN_PROGRESS, BLOCKED, COMPLETED, and ARCHIVED."),
                    List.of(PRIORITY, "Use LOW, MEDIUM, HIGH, or CRITICAL.")
            ), headerStyle);

            addDropdown(workbook.getSheet(VISION_AREAS), 4, "LOW,MEDIUM,HIGH,CRITICAL");
            addDropdown(workbook.getSheet(TASKS), 5, "LOW,MEDIUM,HIGH,CRITICAL");
            addDropdown(workbook.getSheet(TASKS), 8, "NOT_STARTED,IN_PROGRESS,WAITING,BLOCKED,COMPLETED,PAUSED");
            addDropdown(workbook.getSheet(TASKS), 12, "CHARGE,NEUTRAL,DRAIN");

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to export Excel workbook.", exception);
        }
    }

    /**
     * Reads a Vision Mapping export workbook and recreates its Vision Area →
     * Dream → Goal → Step → Task hierarchy for the current user. Runs in one
     * transaction so a broken hierarchy is never half-imported: any row whose
     * required fields are invalid, or whose parent was itself skipped, is
     * skipped with a reported reason rather than aborting the whole import.
     * The other sheets (Partners, Reviews, and so on) are not imported yet.
     */
    @Transactional
    public ExcelImportSummaryResponse importWorkbook(MultipartFile file) {
        List<String> errors = new ArrayList<>();

        // V10 (SEC-05-05, SEC-08-02): refuse files that are not plain .xlsx
        // workbooks, or are too big, before anything else happens.
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            errors.add("Only .xlsx workbooks can be imported.");
            return new ExcelImportSummaryResponse(0, 0, new LinkedHashMap<>(), errors);
        }
        try (Workbook check = open(file)) {
            String problem = refusalReason(check);
            if (problem != null) {
                errors.add(problem);
                return new ExcelImportSummaryResponse(0, 0, new LinkedHashMap<>(), errors);
            }
        } catch (IOException | RuntimeException exception) {
            log.info("Refused an unreadable workbook upload: {}", exception.toString());
            errors.add("This file is not a readable .xlsx workbook.");
            return new ExcelImportSummaryResponse(0, 0, new LinkedHashMap<>(), errors);
        }

        // BRD C-7: snapshot the current data before anything is imported, so a
        // bad import can always be recovered from. A failed snapshot aborts the
        // import — proceeding without the safety copy would defeat its purpose.
        String backupFile;
        try {
            backupFile = saveBackupSnapshot();
        } catch (IOException exception) {
            log.error("Could not save the pre-import backup snapshot", exception);
            errors.add("Import aborted: unable to save the automatic backup snapshot.");
            return new ExcelImportSummaryResponse(0, 0, new LinkedHashMap<>(), errors);
        }

        try (Workbook workbook = open(file)) {
            validateStructure(workbook, errors);
            return new HierarchyImport(visionAreaService, dreamService, goalService, visionStepService, taskItemService, validator)
                    .run(workbook, errors)
                    .withBackupFile(backupFile);
        } catch (IOException exception) {
            log.info("Could not read an uploaded workbook: {}", exception.toString());
            errors.add("This file is not a readable .xlsx workbook.");
            return new ExcelImportSummaryResponse(0, 0, new LinkedHashMap<>(), errors, backupFile);
        }
    }

    private static XSSFWorkbook open(MultipartFile file) throws IOException {
        return new XSSFWorkbook(file.getInputStream());
    }

    /** Null when the workbook may be imported. */
    static String refusalReason(Workbook workbook) {
        if (workbook instanceof XSSFWorkbook xssf && xssf.isMacroEnabled()) {
            return "Workbooks with macros cannot be imported. Save it as a plain .xlsx workbook and try again.";
        }
        if (workbook.getNumberOfSheets() > MAX_SHEETS) {
            return "This workbook has more than " + MAX_SHEETS + " sheets.";
        }
        for (Sheet sheet : workbook) {
            if (sheet.getLastRowNum() > MAX_ROWS_PER_SHEET) {
                return "The sheet \"" + sheet.getSheetName() + "\" has more than " + MAX_ROWS_PER_SHEET + " rows.";
            }
        }
        return null;
    }

    private String saveBackupSnapshot() throws IOException {
        Path directory = Path.of(backupDirectory);
        Files.createDirectories(directory);
        String fileName = "vision-mapping-backup-"
                + LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".xlsx";
        Path target = directory.resolve(fileName);
        Files.write(target, exportWorkbook());
        return target.toAbsolutePath().toString();
    }

    private void validateStructure(Workbook workbook, List<String> errors) {
        for (String sheetName : REQUIRED_SHEETS) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                errors.add("Missing sheet: " + sheetName);
            } else if (sheet.getRow(0) == null) {
                errors.add("Missing header row: " + sheetName);
            }
        }
    }

    private void writeRows(Workbook workbook, String sheetName, List<String> headers, List<? extends List<?>> rows, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        for (int index = 0; index < headers.size(); index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers.get(index));
            cell.setCellStyle(headerStyle);
        }

        // V10 (SEC-05-04): every value is written as text. A value that starts
        // like a formula also gets the "quote prefix" style, so the spreadsheet
        // keeps it as text even after someone edits the cell. Nothing visible
        // changes, and an import reads the same value back.
        CellStyle textOnly = workbook.createCellStyle();
        textOnly.setQuotePrefixed(true);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<?> values = rows.get(rowIndex);
            for (int cellIndex = 0; cellIndex < values.size(); cellIndex++) {
                String text = String.valueOf(value(values.get(cellIndex)));
                Cell cell = row.createCell(cellIndex);
                cell.setCellValue(text);
                if (looksLikeFormula(text)) {
                    cell.setCellStyle(textOnly);
                }
            }
        }

        sheet.createFreezePane(0, 1);
        for (int index = 0; index < headers.size(); index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void addDropdown(Sheet sheet, int columnIndex, String csvValues) {
        if (sheet == null) {
            return;
        }
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper((org.apache.poi.xssf.usermodel.XSSFSheet) sheet);
        CellRangeAddressList range = new CellRangeAddressList(1, 500, columnIndex, columnIndex);
        sheet.addValidationData(helper.createValidation(helper.createExplicitListConstraint(csvValues.split(",")), range));
    }

    static boolean looksLikeFormula(String text) {
        return !text.isEmpty() && FORMULA_TRIGGERS.indexOf(text.charAt(0)) >= 0;
    }

    private Object value(Object value) {
        return value == null ? "" : value;
    }
}
