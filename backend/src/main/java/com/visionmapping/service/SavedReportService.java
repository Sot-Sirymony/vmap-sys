package com.visionmapping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.response.ReportReminderResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.service.report.ReportReminders;
import com.visionmapping.dto.request.SavedReportCommentaryRequest;
import com.visionmapping.dto.request.SavedReportRequest;
import com.visionmapping.dto.response.CommentaryDraftResponse;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.SavedReportResponse;
import com.visionmapping.dto.response.ReportExportResponse;
import com.visionmapping.entity.ReportExport;
import com.visionmapping.entity.SavedReport;
import com.visionmapping.repository.ReportExportRepository;
import com.visionmapping.repository.ReportCoachShareRepository;
import com.visionmapping.repository.ReportShareLinkRepository;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.exception.StateConflictException;
import com.visionmapping.repository.SavedReportRepository;
import com.visionmapping.service.ManagementReportService.Built;
import com.visionmapping.service.ManagementReportService.ReportFile;
import com.visionmapping.service.ManagementReportService.ReportScope;
import com.visionmapping.service.report.ManagementReport;
import com.visionmapping.service.report.ManagementReportPdfWriter;
import com.visionmapping.service.report.ReportCommentaryDrafter;
import com.visionmapping.service.report.ReportComparison;
import com.visionmapping.service.report.ReportExtras;
import com.visionmapping.service.report.ReportSection;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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
    static final String BAD_COMPARISON = "Choose a final report on the same scope to compare with.";

    private final SavedReportRepository repository;
    private final ReportExportRepository exports;
    private final ReportShareLinkRepository shareLinks;
    private final ReportCoachShareRepository coachShares;
    private final ManagementReportService reports;
    private final EntityLookup lookup;
    private final ObjectMapper mapper;
    private final ReportSnapshotCodec codec;
    private final Clock clock;

    public SavedReportService(SavedReportRepository repository, ReportExportRepository exports, ReportShareLinkRepository shareLinks,
                              ReportCoachShareRepository coachShares,
                              ManagementReportService reports, EntityLookup lookup, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.exports = exports;
        this.shareLinks = shareLinks;
        this.coachShares = coachShares;
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
        return toResponse(report, content(report).summary(), null);
    }

    /** RM-FR-13, RM-FR-15: the Details rows, from the snapshot once final. */
    @Transactional(readOnly = true)
    public List<ReportRows.Row> rows(Long id, @Nullable String language) {
        return reports.rows(content(owned(id)).report(), language);
    }

    /** RM-FR-38 (brought forward): the Excel file, from the snapshot once final. */
    public ReportFile excel(Long id, @Nullable String language) {
        return excel(id, language, null);
    }

    /** RM-FR-39 to RM-FR-44: with the commentary, the chosen (or remembered) sections and, for a draft, "DRAFT". */
    public ReportFile excel(Long id, @Nullable String language, @Nullable List<ReportSection> sections) {
        SavedReport saved = owned(id);
        Built content = content(saved);
        ReportFile file = reports.excel(content.report(), content.summary(), languageOf(saved, language),
                fileName(saved, content.report(), "xlsx"), extras(saved, sections));
        recordExport(saved, "xlsx", language);
        return file;
    }

    /** RM-FR-38 (brought forward): the deck, from the snapshot once final. */
    public ReportFile deck(Long id, @Nullable String language, @Nullable String title, boolean includeName) {
        return deck(id, language, title, includeName, null);
    }

    /** RM-FR-39 to RM-FR-44: with the commentary, the chosen (or remembered) sections and, for a draft, "DRAFT". */
    public ReportFile deck(Long id, @Nullable String language, @Nullable String title, boolean includeName,
                           @Nullable List<ReportSection> sections) {
        SavedReport saved = owned(id);
        Built content = content(saved);
        ReportFile file = reports.deck(content.report(), content.summary(), languageOf(saved, language), title, includeName,
                fileName(saved, content.report(), "pptx"), extras(saved, sections));
        recordExport(saved, "pptx", language);
        return file;
    }

    /** RM-FR-42: the PDF, from the snapshot once final; a draft's says "DRAFT" in every footer (RM-FR-43). */
    public ReportFile pdf(Long id, @Nullable String language, @Nullable List<ReportSection> sections) {
        SavedReport saved = owned(id);
        Built content = content(saved);
        String fileLanguage = languageOf(saved, language);
        ReportFile file = reports.pdf(content.report(), content.summary(), fileLanguage, fileName(saved, content.report(), "pdf"),
                extras(saved, sections), cover(saved, content.report(), ReportText.of(fileLanguage)));
        recordExport(saved, "pdf", language);
        return file;
    }

    /** RM-FR-42: the report's name, and "Final · frozen {date}" or "DRAFT · {date}" for the footer. */
    static ManagementReportPdfWriter.Cover cover(SavedReport saved, ManagementReport report, ReportText text) {
        String status = saved.isFinal() && saved.getFinalizedAt() != null
                ? text.format(ReportText.Key.PDF_STATUS_FINAL, text.date(report.asOf()))
                : text.format(ReportText.Key.PDF_STATUS_DRAFT, text.date(report.asOf()));
        return new ManagementReportPdfWriter.Cover(saved.getName(), status);
    }

    /** RM-FR-25: the report's downloads, newest first (the last 50). */
    @Transactional(readOnly = true)
    public List<ReportExportResponse> exports(Long id) {
        SavedReport saved = owned(id);
        return exports.findTop50ByReport_IdAndUser_IdOrderByCreatedAtDescIdDesc(saved.getId(), lookup.userId()).stream()
                .map(export -> new ReportExportResponse(export.getId(), export.getFormat(), export.getLanguage(), export.getCreatedAt()))
                .toList();
    }

    private void recordExport(SavedReport saved, String format, @Nullable String language) {
        exports.save(ReportExport.builder()
                .user(saved.getUser())
                .report(saved)
                .format(format)
                .language(languageOf(saved, language))
                .createdAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS))
                .build());
    }

    /**
     * RM-FR-32: the live report compared with one of the participant's final
     * reports on the same scope, or not compared when {@code compareTo} is null.
     */
    @Transactional(readOnly = true)
    public Built live(ReportScope scope, @Nullable Long compareTo) {
        Built live = reports.live(scope);
        if (compareTo == null) {
            return live;
        }
        SavedReport target = owned(compareTo);
        ManagementReport report = live.report();
        if (!target.isFinal() || !sameScope(target, report.scopeKind(), report.areaId(), report.dreamId())) {
            throw new BusinessRuleException(BAD_COMPARISON);
        }
        return new Built(report, reports.compared(live, earlierOf(target)));
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
        Built built = content(report);
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
        return toResponse(report, content(report).summary(), null);
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
        SavedReport report = editable(id);
        Built content = content(report);
        return suggestions(report, ReportCommentaryDrafter.draft(content.report(), ReportText.of(languageOf(report, language)),
                content.summary().comparison()));
    }

    /**
     * RM-FR-49: whether the participant's report reminder is due now, in their
     * reminder time zone, and the latest final report to start the next from.
     */
    @Transactional(readOnly = true)
    public ReportReminderResponse reminder() {
        AppUser user = lookup.currentUser();
        ZoneId zone = user.getReminderTimeZone() == null ? clock.getZone() : ZoneId.of(user.getReminderTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate dueOn = ReportReminders.lastDue(user.getReportReminder(), user.getReportReminderDay(), today);
        SavedReport latest = repository.findByUser_IdOrderByUpdatedAtDesc(user.getId()).stream()
                .filter(report -> report.isFinal() && !report.isArchived() && report.getFinalizedAt() != null)
                .max(Comparator.comparing(SavedReport::getFinalizedAt)).orElse(null);
        boolean done = latest != null && dueOn != null && !latest.getFinalizedAt().atZone(zone).toLocalDate().isBefore(dueOn);
        boolean due = dueOn != null && !done && ReportReminders.stillShown(dueOn, today);
        return new ReportReminderResponse(due, user.getReportReminder().name(), dueOn,
                latest == null ? null : latest.getId(), latest == null ? null : latest.getName());
    }

    /** RM-FR-29: the fact sheet the AI draft may read, and the language to answer in. */
    public record DraftFacts(String language, String facts) {
    }

    /** RM-FR-29: the AI draft's input, for a draft the participant owns. */
    @Transactional(readOnly = true)
    public DraftFacts commentaryFacts(Long id, @Nullable String language) {
        SavedReport report = editable(id);
        Built content = content(report);
        String fileLanguage = languageOf(report, language);
        return new DraftFacts(fileLanguage, ReportCommentaryDrafter.facts(content.report(), ReportText.of(fileLanguage),
                content.summary().comparison()));
    }

    /** RM-FR-29: the AI draft's text for the sections that are still empty, read again after Claude answered. */
    @Transactional(readOnly = true)
    public CommentaryDraftResponse commentarySuggestions(Long id, ReportCommentaryDrafter.Draft draft) {
        return suggestions(editable(id), draft);
    }

    private SavedReport editable(Long id) {
        SavedReport report = owned(id);
        if (report.isFinal()) {
            throw new StateConflictException(FINAL_READ_ONLY);
        }
        return report;
    }

    private static CommentaryDraftResponse suggestions(SavedReport report, ReportCommentaryDrafter.Draft draft) {
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
        // RM-FR-19: the comparison is frozen with the figures.
        Built built = content(report);
        // MR-NFR-05: the same row limit as the Excel report.
        reports.rows(built.report(), language);
        if (built.summary().comparison() != null) {
            report.setCompareToReportId(built.summary().comparison().reportId());
        }
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
        Built built = content(copy);
        copy.setName(defaultName(built.report(), ReportText.of(copy.getLanguage())));
        return toResponse(repository.save(copy), built.summary(), null);
    }

    /**
     * RM-FR-39: the sections to put in this report's files, remembered for
     * its next download. Allowed on a final report too: it changes how the
     * files are cut, never the frozen figures.
     */
    public SavedReportResponse updateSections(Long id, @Nullable List<ReportSection> sections) {
        SavedReport report = owned(id);
        Set<ReportSection> chosen = sections == null || sections.isEmpty() ? ReportSection.all() : EnumSet.copyOf(sections);
        report.setSections(chosen.equals(ReportSection.all()) ? null : writeJson(new TreeSet<>(chosen)));
        return toResponse(report, null, null);
    }

    /** RM-FR-22 (business rule 12); RM-FR-51: archiving stops the report's open share links and its coach share. */
    public SavedReportResponse archive(Long id, boolean archived) {
        SavedReport report = owned(id);
        report.setArchived(archived);
        if (archived) {
            Instant now = Instant.now(clock);
            shareLinks.findByReport_IdAndUser_IdOrderByCreatedAtDescIdDesc(report.getId(), lookup.userId()).stream()
                    .filter(link -> link.isOpenAt(now))
                    .forEach(link -> link.setRevokedAt(now));
            coachShares.deleteByReportId(report.getId());
        }
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
        boolean comparisonChanged = request.compareToReportId() != null && !request.compareToReportId().equals(report.getCompareToReportId());
        return scopeChanged || periodChanged || filtersChanged || comparisonChanged;
    }

    // ---------- helpers ----------

    SavedReport owned(Long id) {
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
        report.setCompareToReportId(comparisonTarget(report, request.compareToReportId()));
    }

    /** RM-FR-32: a chosen comparison must be another final report of the participant's, on the same scope. Null means automatic. */
    @Nullable
    private Long comparisonTarget(SavedReport report, @Nullable Long compareTo) {
        if (compareTo == null) {
            return null;
        }
        SavedReport target = repository.findByIdAndUser_Id(compareTo, lookup.userId()).orElseThrow(() -> new BusinessRuleException(BAD_COMPARISON));
        if (!target.isFinal() || target.getId().equals(report.getId())
                || !sameScope(target, report.getScopeKind(), report.getAreaId(), report.getDreamId())) {
            throw new BusinessRuleException(BAD_COMPARISON);
        }
        return compareTo;
    }

    private static boolean sameScope(SavedReport target, String scopeKind, @Nullable Long areaId, @Nullable Long dreamId) {
        return target.getScopeKind().equals(scopeKind) && Objects.equals(target.getAreaId(), areaId) && Objects.equals(target.getDreamId(), dreamId);
    }

    /**
     * RM-FR-32: the final report a draft is compared with: the one chosen, if
     * it is still a final report on the same scope, else the latest final
     * report on that scope; null when there is none (RM-FR-36).
     */
    @Nullable
    private ReportComparison.Earlier earlier(SavedReport report) {
        Long userId = lookup.userId();
        SavedReport target = report.getCompareToReportId() == null ? null
                : repository.findByIdAndUser_Id(report.getCompareToReportId(), userId)
                        .filter(SavedReport::isFinal)
                        .filter(found -> sameScope(found, report.getScopeKind(), report.getAreaId(), report.getDreamId()))
                        .orElse(null);
        if (target == null) {
            target = repository.findByUser_IdOrderByUpdatedAtDesc(userId).stream()
                    .filter(SavedReport::isFinal)
                    .filter(found -> !found.getId().equals(report.getId()))
                    .filter(found -> sameScope(found, report.getScopeKind(), report.getAreaId(), report.getDreamId()))
                    .max(Comparator.comparing(SavedReport::getFinalizedAt).thenComparing(SavedReport::getId))
                    .orElse(null);
        }
        return target == null ? null : earlierOf(target);
    }

    private ReportComparison.Earlier earlierOf(SavedReport target) {
        Snapshot snapshot = decode(target).snapshot();
        return new ReportComparison.Earlier(target.getId(), target.getCode(), target.getName(), snapshot.report(), snapshot.summary());
    }

    /** RM-FR-31, RM-FR-39, RM-FR-43: what the files add for a saved report. */
    private ReportExtras extras(SavedReport report, @Nullable List<ReportSection> sections) {
        Set<ReportSection> chosen = sections == null || sections.isEmpty() ? sectionsOf(report) : EnumSet.copyOf(sections);
        return new ReportExtras(commentaryOf(report), chosen, !report.isFinal());
    }

    private Set<ReportSection> sectionsOf(SavedReport report) {
        if (report.getSections() == null) {
            return ReportSection.all();
        }
        List<ReportSection> stored = readJson(report.getSections(), new TypeReference<>() { });
        return stored.isEmpty() ? ReportSection.all() : EnumSet.copyOf(stored);
    }

    SavedReportResponse.Commentary commentaryOf(SavedReport report) {
        return new SavedReportResponse.Commentary(report.getHighlights(), report.getRisks(), report.getDecisionsNeeded(),
                report.isNothingNeeded(), report.getNextFocus(), goalNotesOf(report));
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

    /** What a report shows: live (and compared, RM-FR-32) for a draft, frozen for a final report. */
    Built content(SavedReport report) {
        if (!report.isFinal()) {
            Built live = live(report);
            ReportComparison.Earlier earlier = earlier(report);
            return earlier == null ? live : new Built(live.report(), reports.compared(live, earlier));
        }
        Snapshot snapshot = decode(report).snapshot();
        return new Built(snapshot.report(), snapshot.summary());
    }

    Decoded decode(SavedReport report) {
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
                commentaryOf(report),
                report.getLanguage(), report.getCompareToReportId(), sectionsOf(report).stream().map(Enum::name).toList(), report.getFinalizedAt(), report.getCreatedAt(), report.getUpdatedAt(),
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
