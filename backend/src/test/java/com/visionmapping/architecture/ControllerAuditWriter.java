package com.visionmapping.architecture;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.web.ApiPaths;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * BRD 33 (CS-FR-21): writes {@code docs/CONTROLLER_AUDIT_V33.md}, one line per
 * endpoint with its path today, its version 1 path, its answer today and after
 * V33, its paging, its guard and the phase that moves it.
 *
 * <p>Skipped in a normal build. Run it after each phase to bring the audit up to date:
 * {@code mvn test -Dtest=ControllerAuditWriter -Dvms.audit.write=true}.
 */
class ControllerAuditWriter {

    private static final Path AUDIT = Path.of("../docs/CONTROLLER_AUDIT_V33.md");

    /** BRD 33 §2: the phase that moves each controller. */
    private static final Map<String, Integer> PHASES = phases();

    /** BRD 33 §3: lists that grow without limit and become paged. */
    private static final List<String> PAGED = List.of(
            "/tasks", "/progress-logs", "/communication-messages", "/reviews", "/obstacles", "/partners",
            "/fear-entries", "/gratitude-entries", "/attitude-checkins", "/issue-reports", "/issue-reports/all",
            "/reports/saved", "/tasks/{id}/history",
            "/admin/users", "/admin/security-events", "/admin/client-errors");

    @Test
    void writeTheAudit() throws Exception {
        assumeTrue(Boolean.getBoolean("vms.audit.write"), "set -Dvms.audit.write=true to write the audit");
        ControllerRulesTest.loadClasses();
        List<String> rows = new ArrayList<>();
        int endpoints = 0;
        for (Class<?> controller : ControllerRulesTest.controllers.stream()
                .sorted((a, b) -> a.getSimpleName().compareTo(b.getSimpleName())).toList()) {
            for (Method handler : ControllerRulesTest.handlers(controller)) {
                for (String path : ControllerRulesTest.fullPaths(controller, handler)) {
                    endpoints++;
                    rows.add(row(controller, handler, path));
                }
            }
        }
        Files.writeString(AUDIT, document(rows, endpoints));
    }

    private static String row(Class<?> controller, Method handler, String path) {
        String verbs = verbs(handler);
        return "| " + controller.getSimpleName().replace("Controller", "")
                + " | `" + handler.getName() + "`"
                + " | " + verbs
                + " | `" + path + "`"
                + " | `" + versionOnePath(path) + "`"
                + " | " + answerToday(handler)
                + " | " + answerAfter(handler, verbs)
                + " | " + paging(handler, path, verbs)
                + " | " + guard(handler, path)
                + " | " + PHASES.getOrDefault(controller.getSimpleName(), 0) + " |";
    }

    private static String verbs(Method handler) {
        RequestMethod[] methods = ControllerRulesTest.mapping(handler).method();
        return methods.length == 0 ? "ANY" : Arrays.stream(methods).map(Enum::name).collect(Collectors.joining(", "));
    }

    static String versionOnePath(String path) {
        if (path.equals(ApiPaths.HEALTH) || path.startsWith(ApiPaths.V1 + "/")) {
            return path;
        }
        return ApiPaths.V1 + path.substring(ApiPaths.LEGACY.length());
    }

    private static String answerToday(Method handler) {
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(handler, ResponseStatus.class);
        if (status != null) {
            return String.valueOf((status.code() == HttpStatus.INTERNAL_SERVER_ERROR ? status.value() : status.code()).value());
        }
        if (ResponseEntity.class.isAssignableFrom(handler.getReturnType())) {
            return "set in code";
        }
        return handler.getReturnType() == void.class ? "200, no body" : "200";
    }

    private static String answerAfter(Method handler, String verbs) {
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(handler, ResponseStatus.class);
        HttpStatus code = status == null ? null : (status.code() == HttpStatus.INTERNAL_SERVER_ERROR ? status.value() : status.code());
        if (code == HttpStatus.CREATED) {
            return "201 + Location";
        }
        if (code == HttpStatus.ACCEPTED) {
            return "202";
        }
        if (handler.getReturnType() == void.class || code == HttpStatus.NO_CONTENT) {
            return "204";
        }
        if (ResponseEntity.class.isAssignableFrom(handler.getReturnType())) {
            return "as today";
        }
        return verbs.equals("POST") ? "200 (201 if it creates)" : "200";
    }

    private static String paging(Method handler, String path, String verbs) {
        if (!verbs.equals("GET")) {
            return "-";
        }
        String answer = handler.getGenericReturnType().getTypeName();
        if (answer.contains("org.springframework.data.domain.Page<") || answer.contains("PageResponse")) {
            return "paged today, own shape → PagedResponse";
        }
        String relative = path.substring(ApiPaths.LEGACY.length());
        if (PAGED.contains(relative)) {
            return "paged";
        }
        return Collection.class.isAssignableFrom(handler.getReturnType()) ? "bounded" : "-";
    }

    private static String guard(Method handler, String path) {
        List<String> guards = new ArrayList<>();
        String relative = path.substring(ApiPaths.LEGACY.length());
        if (relative.startsWith("/admin")) {
            guards.add("@PreAuthorize (admin permission)");
        } else if (relative.startsWith("/coach")) {
            guards.add("@PreAuthorize COACH_COHORTS");
        }
        for (Parameter parameter : handler.getParameters()) {
            RequestHeader header = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestHeader.class);
            if (header != null && Set.of(StepUpService.HEADER, StepUpService.CONFIRM_HEADER).contains(header.name())) {
                guards.add("@RequiresStepUp");
                break;
            }
        }
        return guards.isEmpty() ? "-" : String.join(", ", guards);
    }

    private static String document(List<String> rows, int endpoints) {
        return """
                # Controller audit for BRD 33

                Generated on %s by `ControllerAuditWriter` (`mvn test -Dtest=ControllerAuditWriter -Dvms.audit.write=true`).
                Do not edit by hand: change the controllers, then run it again.

                %d endpoints. Columns:

                - **Today / v1 path**: the path now, and under `/api/v1` (D-33-1). `/api/health` stays as it is.
                - **Answer today / after**: the status the endpoint answers now, and after V33 (D-33-6). "set in code"
                  and "as today" mean the handler already builds its own `ResponseEntity`.
                - **Paging**: `paged` for lists that grow without limit (§3), `bounded` for lists to list with a
                  maximum in `docs/API.md`, `-` for everything else.
                - **Guard**: `@PreAuthorize` and `@RequiresStepUp` the endpoint gets in phase 4 (D-33-12, D-33-15).
                - **Phase**: the BRD 33 phase that moves the controller (§2).

                | Controller | Method | HTTP | Today | v1 path | Answer today | Answer after | Paging | Guard | Phase |
                | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
                %s
                """.formatted(LocalDate.now(), endpoints, String.join("\n", rows));
    }

    private static Map<String, Integer> phases() {
        Map<String, Integer> phases = new LinkedHashMap<>();
        for (String name : List.of("VisionArea", "Dream", "Goal", "GoalSynergyLink", "VisionStep", "TaskItem", "TaskRepeatRule",
                "Milestone", "Block", "Obstacle", "ObstacleHelp", "Partner", "IdealPartnerProfile", "CommunicationMessage",
                "CommunicationHelp", "ProgressLog", "ProgressMetrics", "PlanStatus", "Dashboard", "Insight", "DailyPlan", "Review",
                "ReviewRhythm", "Habit", "GratitudeEntry", "FearEntry", "AttitudeCheckIn", "ChainAssessment", "SelfAssessmentItem",
                "StrengthsProfile", "WorkStyleProfile", "WorkStyleHelp")) {
            phases.put(name + "Controller", 2);
        }
        for (String name : List.of("Ai", "AiCoach", "AiDaily", "AiModule", "AiPlan", "PlanAssistant", "PageAssistant",
                "StreamlineReview", "ManagementReport", "SavedReport", "PublicReport", "PlanShare", "Excel", "LearningModule")) {
            phases.put(name + "Controller", 3);
        }
        for (String name : List.of("Auth", "GoogleSignIn", "TwoFactorSignIn", "TwoFactorAccount", "Account", "StepUp",
                "SecurityAccess", "AppearancePreference", "UiText", "ClientErrorLog", "IssueReport", "Health", "AdminUser",
                "AdminStepUp", "Role", "AiAdmin", "AiSettings", "CohortAdmin", "Cohort", "Coach", "LearningModuleAdmin",
                "SecurityLog", "TwoFactorAdmin")) {
            phases.put(name + "Controller", 4);
        }
        return Map.copyOf(phases);
    }
}
