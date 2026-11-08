package com.visionmapping.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Milestone;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.MilestoneRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PdfFonts;
import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-76: one dream's written plan as a PDF. Archived work is left out; Khmer text prints. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanPdfService {

    private static final String[] COLUMNS = {"Work", "Owner", "Date", "Status"};
    private static final float[] COLUMN_WIDTHS = {5f, 2f, 1.5f, 1.5f};

    private final EntityLookup lookup;
    private final PdfFonts pdfFonts;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final MilestoneRepository milestoneRepository;

    public byte[] exportPlan(Long dreamId) {
        Dream dream = lookup.dream(dreamId);
        Long userId = lookup.userId();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            document.addTitle(dream.getTitle());
            document.add(new Paragraph(pdfFonts.phrase(dream.getTitle(), pdfFonts.getTitle())));
            addLabelled(document, "Why it matters", dream.getWhyImportant());
            addLabelled(document, "What success looks like", dream.getSuccessDefinition());
            addLabelled(document, "Target date", dream.getTargetDate());
            if (dream.getCommittedAt() != null) {
                addLabelled(document, "Commitment", "Signed by %s. Starts %s. First action: %s".formatted(
                        dream.getCommitmentSignedName(), dream.getCommitmentStartDate(), dream.getCommitmentFirstAction()));
            }
            List<Goal> goals = goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dreamId, userId).stream()
                    .sorted(Comparator.comparing(Goal::getId))
                    .toList();
            if (goals.isEmpty()) {
                addSection(document, "Goals");
                document.add(new Paragraph(pdfFonts.phrase("No goals yet.", pdfFonts.getBody())));
            }
            for (Goal goal : goals) {
                addSection(document, "Goal: " + goal.getTitle());
                addLabelled(document, "Target date", goal.getTargetDate());
                document.add(workTable(goal, userId));
            }
            List<Milestone> milestones = milestoneRepository.findByDream_IdAndUser_IdAndArchivedFalseOrderByAchievedOnDesc(dreamId, userId);
            if (!milestones.isEmpty()) {
                addSection(document, "Milestones");
                for (Milestone milestone : milestones) {
                    document.add(new Paragraph(pdfFonts.phrase(milestone.getAchievedOn() + "  " + milestone.getTitle(), pdfFonts.getBody())));
                }
            }
            document.close();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Could not build the plan PDF", exception);
        }
        return output.toByteArray();
    }

    private PdfPTable workTable(Goal goal, Long userId) {
        PdfPTable table = new PdfPTable(COLUMN_WIDTHS);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setHeaderRows(1);
        // Khmer vowel signs rise above the Latin cap height and subscripts drop
        // below the baseline, so size rows by the font's real ascent and descent.
        table.getDefaultCell().setUseAscender(true);
        table.getDefaultCell().setUseDescender(true);
        table.getDefaultCell().setPaddingTop(3);
        table.getDefaultCell().setPaddingBottom(4);
        for (String column : COLUMNS) {
            table.addCell(pdfFonts.phrase(column, pdfFonts.getSmallBold()));
        }
        List<VisionStep> steps = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), userId).stream()
                .sorted(Comparator.comparing(VisionStep::getSequenceNumber).thenComparing(VisionStep::getId))
                .toList();
        for (VisionStep step : steps) {
            addRow(table, step.getTitle(), step.getOwner(), step.getTargetDate(), step.getStatus());
            taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), userId).stream()
                    .sorted(Comparator.comparing(TaskItem::getId))
                    .forEach(task -> addRow(table, "    Task: " + task.getTitle(), task.getOwner(), task.getDueDate(), task.getStatus()));
        }
        return table;
    }

    private void addRow(PdfPTable table, String work, String owner, @Nullable Object date, Object status) {
        table.addCell(pdfFonts.phrase(work, pdfFonts.getSmall()));
        table.addCell(pdfFonts.phrase(owner, pdfFonts.getSmall()));
        table.addCell(pdfFonts.phrase(date == null ? "No date" : date.toString(), pdfFonts.getSmall()));
        table.addCell(pdfFonts.phrase(status.toString().replace('_', ' '), pdfFonts.getSmall()));
    }

    private void addSection(Document document, String title) throws DocumentException {
        Paragraph heading = new Paragraph(pdfFonts.phrase(title, pdfFonts.getHeading()));
        heading.setSpacingBefore(14);
        document.add(heading);
    }

    private void addLabelled(Document document, String label, @Nullable Object value) throws DocumentException {
        if (value != null && !value.toString().isBlank()) {
            document.add(new Paragraph(pdfFonts.phrase(label + ": " + value, pdfFonts.getBody())));
        }
    }
}
