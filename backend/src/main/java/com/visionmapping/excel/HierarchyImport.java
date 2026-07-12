package com.visionmapping.excel;

import static com.visionmapping.excel.WorkbookSchema.PRIORITY;
import static com.visionmapping.excel.WorkbookSchema.STATUS;
import static com.visionmapping.excel.WorkbookSchema.TITLE;
import static com.visionmapping.excel.WorkbookSchema.TYPE;

import com.visionmapping.dto.request.DreamRequest;
import com.visionmapping.dto.request.GoalRequest;
import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.request.VisionAreaRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.ExcelImportSummaryResponse;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.GoalService;
import com.visionmapping.service.TaskItemService;
import com.visionmapping.service.VisionStepService;
import com.visionmapping.service.VisionMappingService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Recreates a workbook's Vision Area → Dream → Goal → Step → Task hierarchy
 * for the current user. Each sheet's own "ID" column is the exporting user's
 * database id; it means nothing here, so it is used only to link children to
 * the freshly created parents through the id maps below. A row is skipped
 * (never fatal) when its data is invalid or its parent was itself skipped.
 */
class HierarchyImport {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final VisionMappingService service;
    private final GoalService goalService;
    private final VisionStepService stepService;
    private final TaskItemService taskService;
    private final Map<Long, Long> newVisionAreaIds = new HashMap<>();
    private final Map<Long, Long> newDreamIds = new HashMap<>();
    private final Map<Long, Long> newGoalIds = new HashMap<>();
    private final Map<Long, Long> newStepIds = new HashMap<>();
    private final Map<String, Integer> createdBySheet = new LinkedHashMap<>();

    private List<String> errors;
    private int created;
    private int skipped;

    HierarchyImport(VisionMappingService service, GoalService goalService, VisionStepService stepService, TaskItemService taskService) {
        this.service = service;
        this.goalService = goalService;
        this.stepService = stepService;
        this.taskService = taskService;
    }

    ExcelImportSummaryResponse run(Workbook workbook, List<String> errors) {
        this.errors = errors;
        importVisionAreas(workbook.getSheet("Vision Areas"));
        importDreams(workbook.getSheet("Dreams"));
        importGoals(workbook.getSheet("Goals"));
        importSteps(workbook.getSheet("Steps"));
        importTasks(workbook.getSheet("Tasks"));
        return new ExcelImportSummaryResponse(created, skipped, createdBySheet, errors);
    }

    private void importVisionAreas(Sheet sheet) {
        eachRow(sheet, "Vision Areas", reader -> {
            VisionAreaRequest request = new VisionAreaRequest(
                    reader.requiredText(2, "Name"),
                    reader.textOrNull(3),
                    reader.enumValue(Priority.class, 4, PRIORITY),
                    reader.enumValue(LifecycleStatus.class, 5, STATUS));
            long newId = service.createVisionArea(request).id();
            mapWorkbookId(reader, newVisionAreaIds, newId);
        });
    }

    private void importDreams(Sheet sheet) {
        eachRow(sheet, "Dreams", reader -> {
            long parentId = resolveParent(newVisionAreaIds, reader.requiredReference(2, "Vision Area ID"), "Vision Area");
            DreamRequest request = new DreamRequest(
                    parentId,
                    reader.requiredText(3, TITLE),
                    null,
                    reader.textOrNull(4),
                    reader.textOrNull(5),
                    reader.enumValue(DreamType.class, 6, TYPE),
                    reader.enumValue(Priority.class, 7, PRIORITY),
                    reader.dateOrNull(8),
                    reader.enumValue(DreamStatus.class, 9, STATUS));
            long newId = service.createDream(request).id();
            mapWorkbookId(reader, newDreamIds, newId);
        });
    }

    private void importGoals(Sheet sheet) {
        eachRow(sheet, "Goals", reader -> {
            long parentId = resolveParent(newDreamIds, reader.requiredReference(2, "Dream ID"), "Dream");
            GoalRequest request = new GoalRequest(
                    parentId,
                    reader.requiredText(3, TITLE),
                    null,
                    reader.textOrNull(4),
                    reader.enumValue(Priority.class, 5, PRIORITY),
                    reader.dateOrNull(6),
                    reader.enumValue(WorkStatus.class, 7, STATUS),
                    false,
                    null);
            long newId = goalService.createGoal(request).id();
            mapWorkbookId(reader, newGoalIds, newId);
        });
    }

    private void importSteps(Sheet sheet) {
        eachRow(sheet, "Steps", reader -> {
            long parentId = resolveParent(newGoalIds, reader.requiredReference(2, "Goal ID"), "Goal");
            VisionStepRequest request = new VisionStepRequest(
                    parentId,
                    reader.requiredText(3, TITLE),
                    null,
                    reader.intOrDefault(4, 1),
                    reader.bool(5),
                    reader.enumValue(Priority.class, 6, PRIORITY),
                    reader.dateOrNull(7),
                    reader.enumValue(WorkStatus.class, 8, STATUS));
            long newId = stepService.createStep(request).id();
            mapWorkbookId(reader, newStepIds, newId);
        });
    }

    private void importTasks(Sheet sheet) {
        eachRow(sheet, "Tasks", reader -> {
            long parentId = resolveParent(newStepIds, reader.requiredReference(2, "Step ID"), "Step");
            WorkStatus status = reader.enumValue(WorkStatus.class, 8, STATUS);
            String blockerReason = reader.textOrNull(10);
            if (status == WorkStatus.BLOCKED && blockerReason == null) {
                throw new RowParseException("a blocked task needs a blocker reason");
            }
            BigDecimal progress = reader.decimalOrDefault(9, ZERO);
            if (progress.compareTo(ZERO) < 0 || progress.compareTo(ONE_HUNDRED) > 0) {
                throw new RowParseException("progress must be between 0 and 100");
            }
            TaskItemRequest request = new TaskItemRequest(
                    parentId,
                    reader.requiredText(3, TITLE),
                    null,
                    reader.requiredText(4, "Owner"),
                    reader.enumValue(Priority.class, 5, PRIORITY),
                    reader.dateOrNull(6),
                    reader.requiredDate(7, "Due Date"),
                    status,
                    progress,
                    null,
                    null,
                    blockerReason,
                    reader.textOrNull(11));
            taskService.createTask(request);
        });
    }

    private long resolveParent(Map<Long, Long> parentIds, long workbookParentId, String parentLabel) {
        Long newId = parentIds.get(workbookParentId);
        if (newId == null) {
            throw new RowParseException(parentLabel + " " + workbookParentId + " was not imported");
        }
        return newId;
    }

    private void mapWorkbookId(RowReader reader, Map<Long, Long> idMap, long newId) {
        Long workbookId = reader.referenceOrNull(0);
        if (workbookId != null) {
            idMap.put(workbookId, newId);
        }
    }

    private void eachRow(Sheet sheet, String sheetName, RowImporter importer) {
        createdBySheet.put(sheetName, 0);
        if (sheet == null) {
            return;
        }
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            RowReader reader = new RowReader(row);
            if (reader.isBlank()) {
                continue;
            }
            try {
                importer.importRow(reader);
                created++;
                createdBySheet.merge(sheetName, 1, Integer::sum);
            } catch (RowParseException exception) {
                skipped++;
                errors.add(sheetName + " row " + (rowIndex + 1) + ": " + exception.getMessage());
            }
        }
    }

    @FunctionalInterface
    private interface RowImporter {
        void importRow(RowReader reader);
    }
}
