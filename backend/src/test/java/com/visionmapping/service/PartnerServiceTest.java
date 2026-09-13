package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PartnerServiceTest {

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

    private PartnerService service;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        EntityLookup lookup = new EntityLookup(userScope, visionAreaRepository, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, partnerRepository, communicationMessageRepository,
                reviewRepository, obstacleRepository, progressLogRepository);
        service = new PartnerService(lookup, new VisionMappingMapper(), partnerRepository,
                obstacleRepository, communicationMessageRepository, Clock.systemDefaultZone());
        testUser = AppUser.builder().id(1L).fullName("Test User").email("test@example.com")
                .passwordHash("hash").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        lenient().when(userScope.currentUser()).thenReturn(testUser);
    }

    @Test
    void archivingPartnerSetsArchivedFlagWithoutOverwritingStatus() {
        Partner partner = Partner.builder().id(40L).user(testUser).code("P-001").name("Mentor")
                .supportType(PartnerSupportType.MENTOR).status(PartnerStatus.ACTIVE).build();
        when(partnerRepository.findById(40L)).thenReturn(Optional.of(partner));

        service.archivePartner(40L);

        assertThat(partner.isArchived()).isTrue();
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.ACTIVE);
    }

    private com.visionmapping.dto.request.PartnerRequest requestFrom(Partner partner) {
        return new com.visionmapping.dto.request.PartnerRequest(partner.getName(), partner.getRole(),
                partner.getOrganization(), partner.getEmail(), partner.getPhone(), partner.getStrength(),
                partner.getSupportType(), partner.getOfferType(), null, null, null, null, null,
                partner.getStatus(), partner.getNotes(), partner.getFlagDishonesty(), partner.getFlagAnger(),
                partner.getFlagPoorJudgment(), partner.getFlagOutsizedReward(), partner.getFlagFlatteryPressure(),
                partner.getFlagGossip(), partner.getFlagDisregardBoundaries(), partner.getRiskOverrideNote(),
                partner.getPrimaryMotivator(), partner.getWorkStyleType());
    }

    @Test
    void financialPartnerWithFlaggedConcernAndNoNoteCannotGoActive() {
        Partner partner = Partner.builder().id(41L).user(testUser).code("P-002").name("Investor")
                .supportType(PartnerSupportType.FINANCIAL).status(PartnerStatus.TO_CONTACT)
                .flagOutsizedReward(true).build();
        when(partnerRepository.findById(41L)).thenReturn(Optional.of(partner));

        partner.setStatus(PartnerStatus.ACTIVE);
        assertThatThrownBy(() -> service.updatePartner(41L, requestFrom(partner)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("integrity concerns flagged");
        assertThat(partner.getVettedAt()).isNull();
    }

    @Test
    void financialPartnerWithFlaggedConcernAndNoteCanGoActiveAndIsVetted() {
        Partner partner = Partner.builder().id(42L).user(testUser).code("P-003").name("Investor")
                .supportType(PartnerSupportType.FINANCIAL).status(PartnerStatus.TO_CONTACT)
                .flagOutsizedReward(true).riskOverrideNote("Long track record, proceeding with eyes open.").build();
        when(partnerRepository.findById(42L)).thenReturn(Optional.of(partner));

        partner.setStatus(PartnerStatus.ACTIVE);
        service.updatePartner(42L, requestFrom(partner));

        assertThat(partner.getVettedAt()).isNotNull();
    }

    @Test
    void financialPartnerWithNoFlagsGoesActiveWithNoNoteRequired() {
        Partner partner = Partner.builder().id(43L).user(testUser).code("P-004").name("Investor")
                .supportType(PartnerSupportType.FINANCIAL).status(PartnerStatus.TO_CONTACT).build();
        when(partnerRepository.findById(43L)).thenReturn(Optional.of(partner));

        service.updatePartnerStatus(43L, "ACTIVE");

        assertThat(partner.getVettedAt()).isNotNull();
    }

    @Test
    void gateDoesNotReFireOnceVetted() {
        Partner partner = Partner.builder().id(44L).user(testUser).code("P-005").name("Investor")
                .supportType(PartnerSupportType.FINANCIAL).status(PartnerStatus.ACTIVE)
                .vettedAt(java.time.Instant.parse("2026-01-01T00:00:00Z")).build();
        when(partnerRepository.findById(44L)).thenReturn(Optional.of(partner));

        // Re-saving with a flag set and no note must not re-trigger the gate.
        partner.setFlagAnger(true);
        service.updatePartnerStatus(44L, "ACTIVE");

        assertThat(partner.getVettedAt()).isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void mentorSupportTypeIsNeverGated() {
        Partner partner = Partner.builder().id(45L).user(testUser).code("P-006").name("Mentor")
                .supportType(PartnerSupportType.MENTOR).status(PartnerStatus.TO_CONTACT)
                .flagAnger(true).build();
        when(partnerRepository.findById(45L)).thenReturn(Optional.of(partner));

        service.updatePartnerStatus(45L, "ACTIVE");

        assertThat(partner.getVettedAt()).isNull();
    }
}
