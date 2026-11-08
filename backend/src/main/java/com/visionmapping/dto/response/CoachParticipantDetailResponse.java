package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/**
 * Everything a coach may read about one participant (D-8). The journal lists
 * are null unless the participant shares journals with their coach.
 */
public record CoachParticipantDetailResponse(
        CoachParticipantResponse summary,
        List<DreamResponse> dreams,
        List<GoalResponse> goals,
        List<VisionStepResponse> steps,
        List<TaskItemResponse> tasks,
        boolean journalsShared,
        List<FearEntryResponse> fearEntries,
        List<AttitudeCheckInResponse> attitudeCheckIns,
        List<CoachCommentResponse> comments,
        // V9 (v0.1 FR-11): null until the participant saves a pitch.
        PitchReviewResponse pitch) {
    public CoachParticipantDetailResponse {
        dreams = Copies.list(dreams);
        goals = Copies.list(goals);
        steps = Copies.list(steps);
        tasks = Copies.list(tasks);
        fearEntries = Copies.list(fearEntries);
        attitudeCheckIns = Copies.list(attitudeCheckIns);
        comments = Copies.list(comments);
    }
}
