package com.visionmapping.excel;

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
import com.visionmapping.service.VisionMappingService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ExcelService {

    private static final List<String> REQUIRED_SHEETS = List.of(
            "Dashboard",
            "Vision Areas",
            "Dreams",
            "Goals",
            "Steps",
            "Tasks",
            "Partners",
            "Communication",
            "Reviews",
            "Obstacles",
            "Progress Logs",
            "Instructions"
    );

    private final VisionMappingService visionMappingService;

    public byte[] exportWorkbook() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);

            DashboardSummaryResponse dashboard = visionMappingService.dashboard();
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

            writeRows(workbook, "Vision Areas", List.of("ID", "Code", "Name", "Description", "Priority", "Status"),
                    visionMappingService.listVisionAreas(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.name(), value(item.description()), item.priority(), item.status()))
                            .toList(), headerStyle);

            writeRows(workbook, "Dreams", List.of("ID", "Code", "Vision Area ID", "Title", "Why Important", "Success Definition", "Type", "Priority", "Target Date", "Status"),
                    visionMappingService.listDreams(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.visionAreaId(), item.title(), value(item.whyImportant()), value(item.successDefinition()), item.dreamType(), item.priority(), value(item.targetDate()), item.status()))
                            .toList(), headerStyle);

            writeRows(workbook, "Goals", List.of("ID", "Code", "Dream ID", "Title", "Success Criteria", "Priority", "Target Date", "Status", "Progress"),
                    visionMappingService.listGoals(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.dreamId(), item.title(), value(item.successCriteria()), item.priority(), value(item.targetDate()), item.status(), item.progressPercent()))
                            .toList(), headerStyle);

            writeRows(workbook, "Steps", List.of("ID", "Code", "Goal ID", "Title", "Sequence", "Complex", "Priority", "Target Date", "Status", "Progress"),
                    visionMappingService.listSteps(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.goalId(), item.title(), item.sequenceNumber(), item.complex(), item.priority(), value(item.targetDate()), item.status(), item.progressPercent()))
                            .toList(), headerStyle);

            writeRows(workbook, "Tasks", List.of("ID", "Code", "Step ID", "Title", "Owner", "Priority", "Start Date", "Due Date", "Status", "Progress", "Blocker", "Next Action"),
                    visionMappingService.listTasks(false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.stepId(), item.title(), item.owner(), item.priority(), value(item.startDate()), item.dueDate(), item.status(), item.progressPercent(), value(item.blockerReason()), value(item.nextAction())))
                            .toList(), headerStyle);

            writeRows(workbook, "Partners", List.of("ID", "Code", "Name", "Role", "Organization", "Email", "Support Type", "Status", "Notes"),
                    visionMappingService.listPartners(Pageable.unpaged(), false).stream()
                            .map(item -> List.of(item.id(), item.code(), item.name(), value(item.role()), value(item.organization()), value(item.email()), item.supportType(), item.status(), value(item.notes())))
                            .toList(), headerStyle);

            writeRows(workbook, "Communication", List.of("ID", "Partner ID", "Audience", "Purpose", "Subject", "Request", "Message", "Status", "Follow Up"),
                    visionMappingService.listCommunicationMessages(Pageable.unpaged(), false).stream()
                            .map(item -> List.of(item.id(), value(item.partnerId()), value(item.audience()), value(item.purpose()), value(item.subject()), value(item.request()), value(item.messageBody()), item.status(), value(item.followUpDate())))
                            .toList(), headerStyle);

            writeRows(workbook, "Reviews", List.of("ID", "Type", "Date", "Vision Area ID", "Dream ID", "Summary", "Next Actions"),
                    visionMappingService.listReviews(false).stream()
                            .map(item -> List.of(item.id(), item.reviewType(), item.reviewDate(), value(item.relatedVisionAreaId()), value(item.relatedDreamId()), value(item.summary()), value(item.nextActions())))
                            .toList(), headerStyle);

            writeRows(workbook, "Obstacles", List.of("ID", "Dream ID", "Goal ID", "Step ID", "Task ID", "Title", "Type", "Severity", "Status", "Solution"),
                    visionMappingService.listObstacles(false).stream()
                            .map(item -> List.of(item.id(), value(item.relatedDreamId()), value(item.relatedGoalId()), value(item.relatedStepId()), value(item.relatedTaskId()), item.title(), item.obstacleType(), item.severity(), item.status(), value(item.solution())))
                            .toList(), headerStyle);

            writeRows(workbook, "Progress Logs", List.of("ID", "Task ID", "Before", "After", "Note", "Logged At"),
                    visionMappingService.listProgressLogs().stream()
                            .map(item -> List.of(item.id(), item.relatedTaskId(), item.progressPercentBefore(), item.progressPercentAfter(), value(item.note()), item.loggedAt()))
                            .toList(), headerStyle);

            writeRows(workbook, "Instructions", List.of("Topic", "Instruction"), List.of(
                    List.of("Import", "Use this workbook as a clean template. Keep required sheet names and header rows."),
                    List.of("Export", "Export creates a new workbook and does not overwrite the original workbook."),
                    List.of("Status", "Use application values such as ACTIVE, IN_PROGRESS, BLOCKED, COMPLETED, and ARCHIVED."),
                    List.of("Priority", "Use LOW, MEDIUM, HIGH, or CRITICAL.")
            ), headerStyle);

            addDropdown(workbook.getSheet("Vision Areas"), 4, "LOW,MEDIUM,HIGH,CRITICAL");
            addDropdown(workbook.getSheet("Tasks"), 5, "LOW,MEDIUM,HIGH,CRITICAL");
            addDropdown(workbook.getSheet("Tasks"), 8, "NOT_STARTED,IN_PROGRESS,WAITING,BLOCKED,COMPLETED,PAUSED");

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
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            validateStructure(workbook, errors);
            return new HierarchyImport(visionMappingService).run(workbook, errors);
        } catch (IOException exception) {
            errors.add("Unable to read workbook: " + exception.getMessage());
            return new ExcelImportSummaryResponse(0, 0, new LinkedHashMap<>(), errors);
        }
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

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<?> values = rows.get(rowIndex);
            for (int cellIndex = 0; cellIndex < values.size(); cellIndex++) {
                row.createCell(cellIndex).setCellValue(String.valueOf(value(values.get(cellIndex))));
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

    private Object value(Object value) {
        return value == null ? "" : value;
    }
}
