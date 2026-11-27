package com.visionmapping.service;

import com.visionmapping.dto.response.StreamlineResponses;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V27 (D-27-10): a coach reads a participant's Streamline reviews, read-only,
 * only for a participant they may see. V33 (D-33-11) moved the two calls here
 * from the controller.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CoachStreamlineService {

    private final CoachService coaches;
    private final StreamlineReviewService reviews;

    public List<StreamlineResponses.ListItem> reviewsOf(Long participantId) {
        return reviews.listFor(coaches.requireVisibleParticipant(participantId).getId());
    }

    public StreamlineResponses.Review reviewOf(Long participantId, Long reviewId) {
        return reviews.viewFor(coaches.requireVisibleParticipant(participantId).getId(), reviewId);
    }
}
