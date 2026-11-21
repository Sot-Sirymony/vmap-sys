package com.visionmapping.service.ai;

import com.visionmapping.dto.response.CommentaryDraftResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.service.SavedReportService;
import com.visionmapping.service.report.ReportCommentaryDrafter;
import com.visionmapping.service.support.EntityLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * V19 (RM-FR-29): Draft with AI for a saved report's commentary. Claude reads
 * only the fact sheet the rule-based draft is built from (D-19-2), under the
 * V15 rules: consent, the daily limit and the monthly cap are checked by
 * {@link AiAccessService}, and every request is logged as REPORT_DRAFT.
 * Like the rule-based draft, it only suggests text for empty sections and
 * saves nothing: the participant reviews and edits it first.
 *
 * <p>Not transactional, so no database connection is held while Claude answers.
 */
@Service
@RequiredArgsConstructor
public class ReportCommentaryAiService {

    static final String FEATURE = "REPORT_DRAFT";

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final SavedReportService savedReports;

    public CommentaryDraftResponse draft(Long reportId, @Nullable String language) {
        AppUser user = lookup.currentUser();
        // Ownership and the draft-only rule first (RM-NFR-01): another user's report answers 404 before AI is checked.
        SavedReportService.DraftFacts facts = savedReports.commentaryFacts(reportId, language);
        String apiKey = accessService.requireAvailable(user);
        String task = "Write the participant's commentary for this progress report in four sections: highlights, risks and issues, "
                + "decisions and support needed, and focus for the next period. Use only the facts given; never invent tasks, "
                + "figures, dates or people. Write in the first person, plainly and briefly, as the participant reporting to "
                + "someone who supports them. Give at most five points per section, each on its own line starting with \"- \". "
                + "Leave a section empty when the facts give nothing for it. Answer in "
                + ("km".equals(facts.language()) ? "Khmer" : "English") + ".";
        AiDrafts.ReportCommentaryAiDraft answer = requests.ask(user, apiKey, FEATURE, facts.language(), facts.facts(), task,
                AiDrafts.ReportCommentaryAiDraft.class);
        return savedReports.commentarySuggestions(reportId, new ReportCommentaryDrafter.Draft(
                ReportCommentaryDrafter.bullets(answer.highlights()), ReportCommentaryDrafter.bullets(answer.risks()),
                ReportCommentaryDrafter.bullets(answer.decisionsNeeded()), ReportCommentaryDrafter.bullets(answer.nextFocus())));
    }
}
