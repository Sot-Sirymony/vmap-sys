package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.ReviewRequest;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.GratitudeEntry;
import com.visionmapping.entity.enums.GratitudeCategory;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.GratitudeEntryRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.util.UserScope;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the FR-16/FR-53 diligence rule: the (now ten-item)
 * checklist must be answered as a whole or skipped as a whole, and the
 * FR-53 score is computed only when every item is answered.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private UserScope userScope;
    @Mock private VisionAreaRepository visionAreaRepository;
    @Mock private DreamRepository dreamRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private VisionStepRepository visionStepRepository;
    @Mock private TaskItemRepository taskItemRepository;
    @Mock private PartnerRepository partnerRepository;
    @Mock private CommunicationMessageRepository communicationMessageRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ObstacleRepository obstacleRepository;
    @Mock private ProgressLogRepository progressLogRepository;
    @Mock private GratitudeEntryRepository gratitudeEntryRepository;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        EntityLookup lookup = new EntityLookup(userScope, visionAreaRepository, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, partnerRepository, communicationMessageRepository,
                reviewRepository, obstacleRepository, progressLogRepository);
        service = new ReviewService(lookup, new VisionMappingMapper(), reviewRepository, gratitudeEntryRepository);
        AppUser user = AppUser.builder().id(1L).fullName("Test User").email("test@example.com")
                .passwordHash("hash").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        lenient().when(userScope.currentUser()).thenReturn(user);
    }

    @Test
    void partialDiligenceChecklistIsRejected() {
        ReviewRequest request = new ReviewRequest(ReviewType.WEEKLY, LocalDateTime.now(), null, null,
                "Summary", null, null, null, null, null,
                true, true, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createReview(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Answer every diligence question, or skip the whole checklist.");
    }

    @Test
    void partialAmongTheFiveNewFr53ChecksIsAlsoRejected() {
        ReviewRequest request = new ReviewRequest(ReviewType.WEEKLY, LocalDateTime.now(), null, null,
                "Summary", null, null, null, null, null,
                true, true, true, true, true, true, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createReview(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Answer every diligence question, or skip the whole checklist.");
    }

    @Test
    void fullOrSkippedDiligenceChecklistIsAccepted() {
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewRequest full = new ReviewRequest(ReviewType.WEEKLY, LocalDateTime.now(), null, null,
                "Summary", null, null, null, null, null,
                true, false, true, true, false, true, true, true, true, true, "Tempo weeks slipped", null);
        ReviewRequest skipped = new ReviewRequest(ReviewType.DAILY, LocalDateTime.now(), null, null,
                "Summary", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(service.createReview(full).diligenceWorkedPlan()).isFalse();
        assertThat(service.createReview(skipped).diligenceClearVision()).isNull();
    }

    @Test
    void diligenceScoreIsComputedOnlyWhenAllTenAreAnswered() {
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 7 of 10 true -> 70%.
        ReviewRequest full = new ReviewRequest(ReviewType.MONTHLY, LocalDateTime.now(), null, null,
                "Summary", null, null, null, null, null,
                true, true, true, true, true, true, true, false, false, false, null, null);
        ReviewRequest skipped = new ReviewRequest(ReviewType.DAILY, LocalDateTime.now(), null, null,
                "Summary", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(service.createReview(full).diligenceScorePercent()).isEqualTo(70);
        assertThat(service.createReview(skipped).diligenceScorePercent()).isNull();
    }

    @Test
    void answeringTheGratitudePromptLogsAnOtherCategoryEntry() {
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gratitudeEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ReviewRequest request = new ReviewRequest(ReviewType.WEEKLY, LocalDateTime.now(), null, null,
                "Summary", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, "A good week overall");

        service.createReview(request);

        var captor = org.mockito.ArgumentCaptor.forClass(GratitudeEntry.class);
        verify(gratitudeEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(GratitudeCategory.OTHER);
        assertThat(captor.getValue().getDescription()).isEqualTo("A good week overall");
    }

    @Test
    void leavingTheGratitudePromptBlankLogsNothing() {
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ReviewRequest request = new ReviewRequest(ReviewType.WEEKLY, LocalDateTime.now(), null, null,
                "Summary", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);

        service.createReview(request);

        verify(gratitudeEntryRepository, never()).save(any());
    }
}
