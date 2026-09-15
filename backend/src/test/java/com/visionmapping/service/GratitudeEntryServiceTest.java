package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.GratitudeEntryRequest;
import com.visionmapping.dto.response.GratitudeEntryResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.GratitudeEntry;
import com.visionmapping.entity.enums.GratitudeCategory;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FR-59.1: every field is diagnostic (BR-48) — there is no business-rule
 * gate to test here, only that create/read/update/archive behave like every
 * other user-scoped entity, and that the dashboard summary window/limit
 * (FR-59.2) is correct.
 */
@ExtendWith(MockitoExtension.class)
class GratitudeEntryServiceTest {

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

    private GratitudeEntryService service;
    private AppUser testUser;
    private AppUser otherUser;

    @BeforeEach
    void setUp() {
        EntityLookup lookup = new EntityLookup(userScope, visionAreaRepository, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, partnerRepository, communicationMessageRepository,
                reviewRepository, obstacleRepository, progressLogRepository);
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC);
        service = new GratitudeEntryService(lookup, new VisionMappingMapper(), gratitudeEntryRepository, clock);
        testUser = AppUser.builder().id(1L).fullName("Test User").email("test@example.com")
                .passwordHash("hash").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        otherUser = AppUser.builder().id(2L).fullName("Other User").email("other@example.com")
                .passwordHash("hash").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        lenient().when(userScope.currentUser()).thenReturn(testUser);
    }

    private GratitudeEntry entry(Long id, AppUser owner, GratitudeCategory category, String description) {
        return GratitudeEntry.builder().id(id).user(owner).category(category).description(description).archived(false).build();
    }

    @Test
    void creatingAnEntryNeedsOnlyCategoryAndDescription() {
        when(gratitudeEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GratitudeEntryResponse response = service.createGratitudeEntry(
                new GratitudeEntryRequest(GratitudeCategory.GIFT, "A quiet weekend", null, null));

        assertThat(response.category()).isEqualTo(GratitudeCategory.GIFT);
        assertThat(response.description()).isEqualTo("A quiet weekend");
        assertThat(response.relatedDreamId()).isNull();
    }

    @Test
    void creatingAnEntryOptionallyLinksADream() {
        Dream dream = Dream.builder().id(5L).user(testUser).code("D-001").title("Publish research").build();
        when(dreamRepository.findById(5L)).thenReturn(Optional.of(dream));
        when(gratitudeEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GratitudeEntryResponse response = service.createGratitudeEntry(
                new GratitudeEntryRequest(GratitudeCategory.PERSON, "A mentor's advice", 5L, null));

        assertThat(response.relatedDreamId()).isEqualTo(5L);
    }

    @Test
    void anotherUsersEntryIsNotFound() {
        GratitudeEntry foreign = entry(9L, otherUser, GratitudeCategory.OTHER, "Not mine");
        when(gratitudeEntryRepository.findById(9L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getGratitudeEntry(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void archivingThenPermanentlyDeletingWorks() {
        GratitudeEntry stored = entry(3L, testUser, GratitudeCategory.HEALTH, "A clean bill of health");
        when(gratitudeEntryRepository.findById(3L)).thenReturn(Optional.of(stored));

        service.archiveGratitudeEntry(3L);
        assertThat(stored.isArchived()).isTrue();

        service.permanentlyDeleteGratitudeEntry(3L);
    }

    @Test
    void permanentlyDeletingWithoutArchivingFirstThrows() {
        GratitudeEntry stored = entry(3L, testUser, GratitudeCategory.HEALTH, "A clean bill of health");
        stored.setArchived(false);
        when(gratitudeEntryRepository.findById(3L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.permanentlyDeleteGratitudeEntry(3L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void summaryCountsOnlyTheRollingSevenDayWindow() {
        List<GratitudeEntry> withinWindow = List.of(entry(1L, testUser, GratitudeCategory.OTHER, "One"),
                entry(2L, testUser, GratitudeCategory.OTHER, "Two"));
        when(gratitudeEntryRepository.findByUser_IdAndArchivedFalseAndCreatedAtAfterOrderByCreatedAtDesc(any(), any()))
                .thenReturn(withinWindow);
        when(gratitudeEntryRepository.findByUser_IdAndArchivedFalseOrderByCreatedAtDesc(1L)).thenReturn(withinWindow);

        GratitudeEntryService.GratitudeSummary summary = service.summary();

        assertThat(summary.countThisWeek()).isEqualTo(2);
    }

    @Test
    void summaryCapsRecentEntriesAtThree() {
        List<GratitudeEntry> all = List.of(
                entry(1L, testUser, GratitudeCategory.OTHER, "One"),
                entry(2L, testUser, GratitudeCategory.OTHER, "Two"),
                entry(3L, testUser, GratitudeCategory.OTHER, "Three"),
                entry(4L, testUser, GratitudeCategory.OTHER, "Four"));
        when(gratitudeEntryRepository.findByUser_IdAndArchivedFalseOrderByCreatedAtDesc(1L)).thenReturn(all);

        GratitudeEntryService.GratitudeSummary summary = service.summary();

        assertThat(summary.recent()).hasSize(3);
    }
}
