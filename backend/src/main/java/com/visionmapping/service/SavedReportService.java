package com.visionmapping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.SavedReportCommentaryRequest;
import com.visionmapping.dto.request.SavedReportRequest;
import com.visionmapping.dto.response.CommentaryDraftResponse;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.SavedReportResponse;
import com.visionmapping.entity.SavedReport;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.exception.StateConflictException;
import com.visionmapping.repository.SavedReportRepository;
import com.visionmapping.service.ManagementReportService.Built;
import com.visionmapping.service.ManagementReportService.ReportFile;
import com.visionmapping.service.ManagementReportService.ReportScope;
import com.visionmapping.service.report.ManagementReport;
import com.visionmapping.service.report.ReportCommentaryDrafter;
import com.visionmapping.service.report.ReportFilters;
import com.visionmapping.service.report.ReportPeriod;
import com.visionmapping.service.report.ReportPeriod.Kind;
import com.visionmapping.service.report.ReportRows;
import com.visionmapping.service.report.ReportSnapshotCodec;
import com.visionmapping.service.report.ReportSnapshotCodec.Decoded;
import com.visionmapping.service.report.ReportSnapshotCodec.Encoded;
import com.visionmapping.service.report.ReportSnapshotCodec.Snapshot;
import com.visionmapping.service.report.ReportText;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.ServiceSupport;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V19 (BRD 19.0.0, RM-FR-16 to RM-FR-31): saved Management Reports. A Draft
 * shows the live plan for its scope, period and filters and keeps the
 * participant's commentary. Finalise freezes the model and Summary in a
 * snapshot (D-19-1); a final report is then read-only except for its name,
 * cannot be deleted, and is copied to make the next one. Scoped to the
 * signed-in participant: another user's report answers 404 (RM-NFR-01).
 */
@Service
@Transactional
public class SavedReportService {

    /** RM-FR-24: saved reports per participant, archived ones included. */
    public static final int MAX_REPORTS = 200;
    static final String NOT_FOUND = "Report not found.";
    static final String AT_LIMIT = "You have 200 saved reports. Delete drafts you no longer need first.";
    static final String FINAL_READ_ONLY = "A final report cannot be changed. Make a new report from it instead.";
    static final String FINAL_NOT_DELETED = "A final report cannot be deleted. You can archive it.";
    static final String ALREADY_FINAL = "This report is already final.";
    static final String SCOPE_GONE = "The vision area or dream this report covers no longer exists.";

    private final SavedReportRepository repository;
    private final ManagementReportService reports;
    private final EntityLookup lookup;
    private final ObjectMapper mapper;
    private final ReportSnapshotCodec codec;
    private final Clock clock;

    public SavedReportService(SavedReportRepository repository, ManagementReportService reports, EntityLookup lookup,
                              ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.reports = reports;
        this.lookup = lookup;
        this.mapper = mapper;
        this.codec = new ReportSnapshotCodec(mapper);
        this.clock = clock;
    }

    // ---------- reading ----------

    /** RM-FR-17: newest change first; archived ones only when asked for. */
    @Transactional(readOnly = true)
    public List<SavedReportResponse> list(@Nullable String status, boolean includeArchived, @Nullable String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return repository.findByUser_IdOrderByUpdatedAtDesc(lookup.userId()).stream()
                .filter(report -> includeArchived || !report.isArchived())
                .filter(report -> status == null || status.isBlank() || status.equalsIgnoreCase(report.getStatus()))
                .filter(report -> needle.isEmpty() || report.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || report.getCode().toLowerCase(Locale.ROOT).contains(needle))
                .map(report -> toResponse(report, null, null))
                .toList();
    }

    /** RM-FR-18, RM-FR-20: a draft with live figures, or a final report from its snapshot only. */
    @Transactional(readOnly = true)
    public SavedReportResponse get(Long id) {
        SavedReport report = owned(id);
        if (report.isFinal()) {
            Decoded decoded = decode(report);
            return toResponse(report, decoded.snapshot().summary(), decoded.verified());
        }
        return toResponse(report, live(report).summary(), null);
    }

    /** RM-FR-13, RM-FR-15: the Details rows, from the snapshot once final. */
    @Transactional(readOnly = true)
    public List<ReportRows.Row> rows(Long id, @Nullable String language) {
        return reports.rows(content(owned(id)).report(), language);
    }

    /** RM-FR-38 (brought forward): the Excel file, from the snapshot once final. */
    @Transactional(readOnly = true)
    public ReportFile excel(Long id, @Nullable String language) {
        SavedReport saved = owned(id);
        Built content = content(saved);
        return reports.excel(content.report(), content.summary(), languageOf(saved, language), fileName(saved, content.report(), "xlsx"));
    }

    /** RM-FR-38 (brought forward): the deck, from the snapshot once final. */
    @Transactional(readOnly = true)
    public ReportFile deck(Long id, @Nullable String language, @Nullable String title, boolean includeName) {
        SavedReport saved = owned(id);
        Built content = content(saved);
        return reports.deck(content.report(), content.summary(), languageOf(saved, language), title, includeName,
                fileName(saved, content.report(), "pptx"));
    }

    // ---------- lifecycle ----------

    /** RM-FR-16: a new draft for the scope, period and filters in use. */
    public SavedReportResponse create(SavedReportRequest request) {
        Long userId = lookup.userId();
        if (repository.countByUser_Id(userId) >= MAX_REPORTS) {
            throw new BusinessRuleException(AT_LIMIT);
        }
        String language = request.language() == null ? "en" : request.language();
        SavedReport report = SavedReport.builder()
                .user(lookup.currentUser())
                .code(ServiceSupport.nextCode("R", repository.findCodesByUserId(userId)))
                .status(SavedReport.DRAFT)
                .language(language)
                .build();
        applySettings(report, request);
        // Checks the scope is the participant's and the period is valid, and gives the default name.
        Built built = live(report);
        report.setName(isBlank(request.name()) ? defaultName(built.report(), ReportText.of(language)) : request.name().strip());
        return toResponse(repository.save(report), built.summary(), null);
    }

    /** RM-FR-18, RM-FR-23: a draft's settings change freely; a final report may only be renamed. */
    public SavedReportResponse update(Long id, SavedReportRequest request) {
        SavedReport report = owned(id);
        if (report.isFinal()) {
            if (changesMoreThanTheName(report, request)) {
                throw new StateConflictException(FINAL_READ_ONLY);
            }
            rename(report, request.name());
            return get(id);
        }
        applySettings(report, request);
        rename(report, request.name());
        Built built = live(report);
        return toResponse(report, built.summary(), null);
    }

    /** RM-FR-26, RM-FR-27, RM-FR-30: the commentary, saved as the participant types. */
    public SavedReportResponse updateCommentary(Long id, SavedReportCommentaryRequest request) {
        SavedReport report = owned(id);
        if (report.isFinal()) {
            throw new StateConflictException(FINAL_READ_ONLY);
        }
        report.setHighlights(blankToNull(request.highlights()));
        report.setRisks(blankToNull(request.risks()));
        report.setDecisionsNeeded(blankToNull(request.decisionsNeeded()));
        report.setNothingNeeded(request.nothingNeeded());
        report.setNextFocus(blankToNull(request.nextFocus()));
        Map<Long, String> notes = request.goalNotes() == null ? Map.of() : request.goalNotes();
        report.setGoalNotes(writeJson(notes.entrySet().stream()
                .filter(entry -> !isBlank(entry.getValue()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().strip(),
                        (first, second) -> first, java.util.TreeMap::new))));
        return toResponse(report, null, null);
    }

    /**
     * RM-FR-28: suggested text for the sections still empty, from fixed rules.
     * Nothing is saved; the page fills the empty fields and its autosave keeps them.
     */
    @Transactional(readOnly = true)
    public CommentaryDraftResponse commentaryDraft(Long id, @Nullable String language) {
        SavedReport report = owned(id);
        if (report.isFinal()) {
            throw new StateConflictException(FINAL_READ_ONLY);
        }
        ReportCommentaryDrafter.Draft draft = ReportCommentaryDrafter.draft(live(report).report(), ReportText.of(languageOf(report, language)));
        return new CommentaryDraftResponse(
                suggestion(report.getHighlights(), draft.highlights()),
                suggestion(report.getRisks(), draft.risks()),
                report.isNothingNeeded() ? null : suggestion(report.getDecisionsNeeded(), draft.decisionsNeeded()),
                suggestion(report.getNextFocus(), draft.nextFocus()));
    }

    /** RM-FR-19, D-19-1: freeze the figures as of today. */
    public SavedReportResponse finalizeReport(Long id, @Nullable String language) {
        SavedReport report = owned(id);
        if (report.isFinal()) {
            throw new StateConflictException(ALREADY_FINAL);
        }
        Built built = live(report);
        // MR-NFR-05: the same row limit as the Excel report.
        reports.rows(built.report(), language);
        Encoded encoded = codec.encode(new Snapshot(built.report(), built.summary()));
        ReportPeriod period = built.report().period();
        report.setPeriodKind(period.kind().name());
        report.setPeriodFrom(period.from());
        report.setPeriodTo(period.to());
        report.setScopeLabel(built.report().scopeLabel());
        report.setLanguage(languageOf(report, language));
        report.setSnapshot(encoded.data());
        report.setSnapshotChecksum(encoded.checksum());
        report.setSnapshotVersion(encoded.version());
        report.setStatus(SavedReport.FINAL);
        report.setFinalizedAt(Instant.now(clock));
        return toResponse(report, built.summary(), true);
    }

    /**
     * RM-FR-21: a new draft with the same scope and filters, the next period
     * of the same length, the commentary as a starting point, and the source
     * set as the report to compare with.
     */
    public SavedReportResponse copy(Long id) {
        SavedReport source = owned(id);
        Long userId = lookup.userId();
        if (repository.countByUser_Id(userId) >= MAX_REPORTS) {
            throw new BusinessRuleException(AT_LIMIT);
        }
        LocalDate today = LocalDate.now(clock);
        ReportPeriod sourcePeriod = source.isFinal()
                ? new ReportPeriod(Kind.valueOf(source.getPeriodKind()), source.getPeriodFrom(), source.getPeriodTo())
                : ReportPeriod.resolve(source.getPeriodKind(), source.getPeriodFrom(), source.getPeriodTo(), today);
        ReportPeriod next = nextPeriod(sourcePeriod, today);
        SavedReport copy = SavedReport.builder()
                .user(lookup.currentUser())
                .code(ServiceSupport.nextCode("R", repository.findCodesByUserId(userId)))
                .status(SavedReport.DRAFT)
                .language(source.getLanguage())
                .scopeKind(source.getScopeKind())
                .areaId(source.getAreaId())
                .dreamId(source.getDreamId())
                .scopeLabel(source.getScopeLabel())
                .periodKind(next.kind().name())
                .periodFrom(next.kind() == Kind.CUSTOM ? next.from() : null)
                .periodTo(next.kind() == Kind.CUSTOM ? next.to() : null)
                .filters(source.getFilters())
                .highlights(source.getHighlights())
                .risks(source.getRisks())
                .decisionsNeeded(source.getDecisionsNeeded())
                .nothingNeeded(source.isNothingNeeded())
                .nextFocus(source.getNextFocus())
                .goalNotes(source.getGoalNotes())
                .sections(source.getSections())
                .compareToReportId(source.isFinal() ? source.getId() : source.getCompareToReportId())
                .build();
        Built built = live(copy);
        copy.setName(defaultName(built.report(), ReportText.of(copy.getLanguage())));
        return toResponse(repository.save(copy), built.summary(), null);
    }

    /** RM-FR-22 (business rule 12). */
    public SavedReportResponse archive(Long id, boolean archived) {
        SavedReport report = owned(id);
        report.setArchived(archived);
        return toResponse(report, null, null);
    }

    /** RM-FR-22: only a draft may be deleted for good; it holds no frozen record. */
    public void delete(Long id) {
        SavedReport report = owned(id);
        if (report.isFinal()) {
            throw new StateConflictException(FINAL_NOT_DELETED);
        }
        repository.delete(report);
    }

    // ---------- rules ----------

    /**
     * The period after {@code source}, of the same length: the next week,
     * month or quarter, or as many days for a custom period. A period still
     * running becomes the matching "this …" preset (or ends today); one wholly
     * past keeps its dates.
     */
    static ReportPeriod nextPeriod(ReportPeriod source, LocalDate today) {
        LocalDate from;
        LocalDate to;
        Kind running;
        switch (source.kind()) {
            case THIS_WEEK, LAST_WEEK -> {
                from = source.from().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
                to = from.plusDays(6);
                running = Kind.THIS_WEEK;
            }
            case THIS_MONTH, LAST_MONTH -> {
                from = source.from().withDayOfMonth(1).plusMonths(1);
                to = from.plusMonths(1).minusDays(1);
                running = Kind.THIS_MONTH;
            }
            case THIS_QUARTER, LAST_QUARTER -> {
                from = source.from().with(IsoFields.DAY_OF_QUARTER, 1).plusMonths(3);
                to = from.plusMonths(3).minusDays(1);
                running = Kind.THIS_QUARTER;
            }
            default -> {
                from = source.to().plusDays(1);
                to = from.plusDays(ChronoUnit.DAYS.between(source.from(), source.to()));
                running = null;
            }
        }
        if (to.isBefore(today)) {
            return new ReportPeriod(Kind.CUSTOM, from, to);
        }
        if (running != null) {
            return ReportPeriod.resolve(running.name(), null, null, today);
        }
        return from.isAfter(today) ? ReportPeriod.resolve(null, null, null, today) : new ReportPeriod(Kind.CUSTOM, from, today);
    }

    /** "Fixture dream – This month", or "All dreams – 1 Nov 2026 to 10 Nov 2026". */
    static String defaultName(ManagementReport report, ReportText text) {
        String scope = "ALL".equals(report.scopeKind()) || report.scopeLabel() == null ? text.get(ReportText.Key.ALL_DREAMS) : report.scopeLabel();
        String name = scope + " – " + text.periodLabel(report.period());
        return name.length() <= 120 ? name : name.substring(0, 119) + "…";
    }

    /**
     * RM-FR-38: "{Report name}_{yyyy-MM-dd}.ext" with only letters, digits,
     * dash and underscore, at most 80 characters before the date; the scope's
     * name when nothing safe is left.
     */
    static String fileName(SavedReport saved, ManagementReport report, String extension) {
        String safe = saved.getName().strip().replaceAll("\\s+", "_").replaceAll("[^A-Za-z0-9_-]", "").replaceAll("_+", "_");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        if (safe.replace("_", "").replace("-", "").isEmpty()) {
            return ManagementReportService.fileName(report, extension);
        }
        return safe + "_" + report.asOf() + "." + extension;
    }

    /** A rename of a final report sends the name; any other field sent with a different value is a change. */
    private static boolean changesMoreThanTheName(SavedReport report, SavedReportRequest request) {
        boolean scopeChanged = request.areaId() != null && !request.areaId().equals(report.getAreaId())
                || request.dreamId() != null && !request.dreamId().equals(report.getDreamId());
        boolean periodChanged = request.period() != null && !request.period().equalsIgnoreCase(report.getPeriodKind())
                || request.from() != null && !request.from().equals(report.getPeriodFrom())
                || request.to() != null && !request.to().equals(report.getPeriodTo());
        boolean filtersChanged = !orEmpty(request.priorities()).isEmpty() || !isBlank(request.owner()) || !orEmpty(request.healths()).isEmpty()
                || Boolean.FALSE.equals(request.includeCompleted());
        return scopeChanged || periodChanged || filtersChanged;
    }

    // ---------- helpers ----------

    private SavedReport owned(Long id) {
        return repository.findByIdAndUser_Id(id, lookup.userId()).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private void applySettings(SavedReport report, SavedReportRequest request) {
        if (request.areaId() != null && request.dreamId() != null) {
            throw new BusinessRuleException(ManagementReportService.ONE_SCOPE);
        }
        String scopeKind = "ALL";
        if (request.dreamId() != null) {
            scopeKind = "DREAM";
        } else if (request.areaId() != null) {
            scopeKind = "AREA";
        }
        report.setScopeKind(scopeKind);
        report.setAreaId(request.areaId());
        report.setDreamId(request.dreamId());
        Kind kind = ReportPeriod.resolve(request.period(), request.from(), request.to(), LocalDate.now(clock)).kind();
        report.setPeriodKind(kind.name());
        report.setPeriodFrom(kind == Kind.CUSTOM ? request.from() : null);
        report.setPeriodTo(kind == Kind.CUSTOM ? request.to() : null);
        ReportFilters filters = new ReportFilters(new HashSet<>(orEmpty(request.priorities())), request.owner(), new HashSet<>(orEmpty(request.healths())),
                request.includeCompleted() == null || request.includeCompleted());
        report.setFilters(filters.isEmpty() ? null : writeJson(filters));
    }

    private static void rename(SavedReport report, @Nullable String name) {
        if (!isBlank(name)) {
            report.setName(name.strip());
        }
    }

    /** A draft's live model and Summary for its saved scope, period and filters. */
    private Built live(SavedReport report) {
        boolean scopeGone = ("AREA".equals(report.getScopeKind()) && report.getAreaId() == null)
                || ("DREAM".equals(report.getScopeKind()) && report.getDreamId() == null);
        if (scopeGone) {
            throw new BusinessRuleException(SCOPE_GONE);
        }
        Built built = reports.live(new ReportScope(report.getAreaId(), report.getDreamId(), report.getPeriodKind(), report.getPeriodFrom(),
                report.getPeriodTo(), filtersOf(report)));
        report.setScopeLabel(built.report().scopeLabel());
        return built;
    }

    /** What a report shows: live for a draft, frozen for a final report. */
    private Built content(SavedReport report) {
        if (!report.isFinal()) {
            return live(report);
        }
        Snapshot snapshot = decode(report).snapshot();
        return new Built(snapshot.report(), snapshot.summary());
    }

    private Decoded decode(SavedReport report) {
        return codec.decode(report.getSnapshot(), report.getSnapshotChecksum(), report.getSnapshotVersion());
    }

    private ReportFilters filtersOf(SavedReport report) {
        return report.getFilters() == null ? ReportFilters.NONE : readJson(report.getFilters(), new TypeReference<>() { });
    }

    private Map<Long, String> goalNotesOf(SavedReport report) {
        return report.getGoalNotes() == null ? Map.of() : readJson(report.getGoalNotes(), new TypeReference<>() { });
    }

    private static String languageOf(SavedReport report, @Nullable String language) {
        return language == null || language.isBlank() ? report.getLanguage() : language;
    }

    private SavedReportResponse toResponse(SavedReport report, @Nullable ManagementReportResponse summary, @Nullable Boolean verified) {
        ReportFilters filters = filtersOf(report);
        return new SavedReportResponse(report.getId(), report.getCode(), report.getName(), report.getStatus(), report.isArchived(),
                report.getScopeKind(), report.getAreaId(), report.getDreamId(), report.getScopeLabel(), report.getPeriodKind(),
                report.getPeriodFrom(), report.getPeriodTo(),
                new ManagementReportResponse.Filters(filters.priorities().stream().sorted().map(Enum::name).toList(), filters.owner(),
                        filters.healths().stream().sorted().map(Enum::name).toList(), filters.includeCompleted()),
                new SavedReportResponse.Commentary(report.getHighlights(), report.getRisks(), report.getDecisionsNeeded(),
                        report.isNothingNeeded(), report.getNextFocus(), goalNotesOf(report)),
                report.getLanguage(), report.getCompareToReportId(), report.getFinalizedAt(), report.getCreatedAt(), report.getUpdatedAt(),
                verified, summary);
    }

    @Nullable
    private static String suggestion(@Nullable String current, String suggested) {
        return isBlank(current) && !suggested.isEmpty() ? suggested : null;
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to save the report settings.", exception);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read the report settings.", exception);
        }
    }

    private static <T> List<T> orEmpty(@Nullable List<T> values) {
        return values == null ? List.of() : values;
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return isBlank(value) ? null : value.strip();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
