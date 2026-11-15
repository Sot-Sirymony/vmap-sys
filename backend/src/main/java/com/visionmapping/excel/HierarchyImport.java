package com.visionmapping.excel;

import java.util.Set;
import java.util.Comparator;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import static com.visionmapping.excel.WorkbookSchema.PRIORITY;
import static com.visionmapping.excel.WorkbookSchema.STATUS;
import static com.visionmapping.excel.WorkbookSchema.TITLE;
import static com.visionmapping.excel.WorkbookSchema.TYPE;

import com.visionmapping.dto.request.DreamRequest;
import com.visionmapping.dto.request.GoalRequest;
import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.request.VisionAreaRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.ExcelImportSummaryResponse;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.ObstaclePlacement;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ScheduleMode;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.DreamService;
import com.visionmapping.service.VisionAreaService;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.service.GoalService;
import com.visionmapping.service.ObstacleService;
import com.visionmapping.service.TaskItemService;
import com.visionmapping.service.VisionStepService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.lang.Nullable;

/**
 * Recreates a workbook's Vision Area → Dream → Goal → Step → Task hierarchy
 * for the current user. Each sheet's own "ID" column is the exporting user's
 * database id; it means nothing here, so it is used only to link children to
 * the freshly created parents through the id maps below. A row is skipped
 * (never fatal) when its data is invalid or its parent was itself skipped.
 *
 * <p>V17 BL-FR-41: obstacles are imported last. A row with a Task or Step ID
 * is placed there; a row with only a Dream or Goal ID is imported as "not
 * placed" and reported as a warning, never skipped.
 */
class HierarchyImport {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final VisionAreaService visionAreaService;
    private final DreamService dreamService;
    private final GoalService goalService;
    private final VisionStepService stepService;
    private final TaskItemService taskService;
    private final ObstacleService obstacleService;
    private final Validator validator;
    private final Map<Long, Long> newVisionAreaIds = new HashMap<>();
    private final Map<Long, Long> newDreamIds = new HashMap<>();
    private final Map<Long, Long> newGoalIds = new HashMap<>();
    private final Map<Long, Long> newStepIds = new HashMap<>();
    private final Map<Long, Long> newTaskIds = new HashMap<>();
    // New task id → its new step id, to spot an obstacle row whose Step ID and Task ID disagree.
    private final Map<Long, Long> newTaskStepIds = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private int unplacedObstacles;
    private final Map<String, Integer> createdBySheet = new LinkedHashMap<>();

    private List<String> errors;
    private int created;
    private int skipped;

    HierarchyImport(VisionAreaService visionAreaService, DreamService dreamService, GoalService goalService, VisionStepService stepService, TaskItemService taskService,
                    ObstacleService obstacleService, Validator validator) {
        this.visionAreaService = visionAreaService;
        this.dreamService = dreamService;
        this.goalService = goalService;
        this.stepService = stepService;
        this.taskService = taskService;
        this.obstacleService = obstacleService;
        this.validator = validator;
    }

    ExcelImportSummaryResponse run(Workbook workbook, List<String> errors) {
        this.errors = errors;
        importVisionAreas(workbook.getSheet("Vision Areas"));
        importDreams(workbook.getSheet("Dreams"));
        importGoals(workbook.getSheet("Goals"));
        importSteps(workbook.getSheet("Steps"));
        importTasks(workbook.getSheet("Tasks"));
        importObstacles(workbook.getSheet(WorkbookSchema.OBSTACLES));
        if (unplacedObstacles > 0) {
            warnings.add(0, "Obstacles: imported, needs a step (" + unplacedObstacles + "). Choose a step for each on the Obstacles page.");
        }
        return new ExcelImportSummaryResponse(created, skipped, createdBySheet, errors, warnings);
    }

    private void importVisionAreas(Sheet sheet) {
        eachRow(sheet, "Vision Areas", reader -> {
            VisionAreaRequest request = new VisionAreaRequest(
                    reader.requiredText(2, "Name"),
                    reader.textOrNull(3),
                    null,
                    reader.enumValue(Priority.class, 4, PRIORITY),
                    null,
                    reader.enumValue(LifecycleStatus.class, 5, STATUS));
            long newId = visionAreaService.createVisionArea(valid(request)).id();
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
                    null,
                    reader.dateOrNull(8),
                    reader.enumValue(DreamStatus.class, 9, STATUS),
                    false,
                    null,
                    null,
                    ScheduleMode.BOTTOM_UP,
                    null, null, null, null, null, null, null, null);
            long newId = dreamService.createDream(valid(request)).id();
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
                    null,
                    reader.dateOrNull(6),
                    reader.enumValue(WorkStatus.class, 7, STATUS),
                    false,
                    null,
                    ScheduleMode.BOTTOM_UP);
            long newId = goalService.createGoal(valid(request)).id();
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
                    reader.textOrNull(10),
                    reader.intOrDefault(4, 1),
                    reader.bool(5),
                    reader.enumValue(Priority.class, 6, PRIORITY),
                    reader.dateOrNull(7),
                    reader.enumValue(WorkStatus.class, 8, STATUS),
                    null, null, null);
            long newId = stepService.createStep(valid(request)).id();
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
                    null,
                    reader.dateOrNull(6),
                    reader.requiredDate(7, "Due Date"),
                    status,
                    progress,
                    null,
                    null,
                    blockerReason,
                    reader.textOrNull(11),
                    // FR-34: energy demand isn't imported from the workbook — it's
                    // an in-app tag; existing rows read as NEUTRAL (BR-27).
                    null);
            TaskItemResponse task = taskService.createTask(valid(request));
            Long workbookId = reader.referenceOrNull(0);
            if (workbookId != null) {
                newTaskIds.put(workbookId, task.id());
                newTaskStepIds.put(task.id(), task.stepId());
            }
        });
    }

    /**
     * V17 BL-FR-41. Columns: ID, Dream ID, Goal ID, Step ID, Task ID, Title,
     * Type, Severity, Status, Solution (and "Placed on", which is ignored).
     * Resolved and Accepted rows are skipped with a reason: the app needs a
     * root cause or three alternatives for them, and the sheet holds neither.
     */
    private void importObstacles(Sheet sheet) {
        eachRow(sheet, WorkbookSchema.OBSTACLES, reader -> {
            int rowNumber = reader.rowNumber();
            ObstacleStatus status = reader.enumValue(ObstacleStatus.class, 8, STATUS);
            if (status == ObstacleStatus.RESOLVED || status == ObstacleStatus.ACCEPTED) {
                throw new RowParseException("a " + status.name().toLowerCase(java.util.Locale.ROOT)
                        + " obstacle needs a root cause or alternatives, which the workbook does not hold; add it in the app");
            }
            Long taskId = mapped(newTaskIds, reader.referenceOrNull(4));
            Long stepId = mapped(newStepIds, reader.referenceOrNull(3));
            if (reader.referenceOrNull(4) != null && taskId == null) {
                warnings.add("Obstacles row " + rowNumber + ": its task was not imported, so it was put on its step instead.");
            }
            if (taskId != null && stepId != null && !stepId.equals(newTaskStepIds.get(taskId))) {
                warnings.add("Obstacles row " + rowNumber + ": its Step ID and Task ID disagree; it was put on the task.");
            }
            Long goalId = mapped(newGoalIds, reader.referenceOrNull(2));
            Long dreamId = mapped(newDreamIds, reader.referenceOrNull(1));
            ObstacleRequest request = new ObstacleRequest(dreamId, goalId, taskId != null ? null : stepId, taskId,
                    reader.requiredText(5, TITLE), null,
                    reader.enumValue(ObstacleType.class, 6, TYPE),
                    reader.enumValue(Severity.class, 7, "Severity"),
                    null, reader.textOrNull(9), null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, status);
            ObstacleResponse response;
            try {
                response = obstacleService.createObstacleAllowingUnplaced(valid(request));
            } catch (BusinessRuleException exception) {
                throw new RowParseException(exception.getMessage());
            }
            if (response.placement() == ObstaclePlacement.NONE) {
                unplacedObstacles++;
            }
        });
    }

    @Nullable
    private static Long mapped(Map<Long, Long> idMap, @Nullable Long workbookId) {
        return workbookId == null ? null : idMap.get(workbookId);
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

    private void eachRow(@Nullable Sheet sheet, String sheetName, RowImporter importer) {
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

    /**
     * V10 (SEC-08-01): the same checks a form submission gets, before anything
     * is saved. A row that fails is skipped with the reason, instead of failing
     * later at the database.
     */
    private <T> T valid(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.stream()
                    .min(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .orElseThrow();
            throw new RowParseException(first.getPropertyPath() + ": " + first.getMessage());
        }
        return request;
    }

    @FunctionalInterface
    private interface RowImporter {
        void importRow(RowReader reader);
    }
}
