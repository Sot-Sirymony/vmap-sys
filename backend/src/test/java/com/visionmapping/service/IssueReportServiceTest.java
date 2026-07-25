package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.IssueReportRequest;
import com.visionmapping.dto.response.IssueReportResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.IssueReport;
import com.visionmapping.entity.enums.IssueReportStatus;
import com.visionmapping.entity.enums.ReportType;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.IssueReportRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.util.UserScope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * FR-38 / BR-32: a bug needs a severity; a resolved bug needs a resolution
 * note; only an ADMIN triages the queue; a user can never reach another user's
 * report.
 */
@ExtendWith(MockitoExtension.class)
class IssueReportServiceTest {

    @Mock private UserScope userScope;
    @Mock private VisionAreaRepository visionAreaRepository;
    @Mock private DreamRepository dreamRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private VisionStepRepository visionStepRepository;
    @Mock private TaskItemRepository taskItemRepository;
    @Mock private PartnerRepository partnerRepository;
    @Mock private CommunicationMessageRepository communicationMessageRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ProgressLogRepository progressLogRepository;
    @Mock private ObstacleRepository obstacleRepository;
    @Mock private IssueReportRepository issueReportRepository;

    private IssueReportService service;
    private AppUser user;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        EntityLookup lookup = new EntityLookup(userScope, visionAreaRepository, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, partnerRepository, communicationMessageRepository,
                reviewRepository, obstacleRepository, progressLogRepository);
        service = new IssueReportService(lookup, new VisionMappingMapper(), issueReportRepository);

        user = AppUser.builder().id(1L).fullName("Test User").email("test@example.com")
                .passwordHash("hash").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        admin = AppUser.builder().id(2L).fullName("Admin User").email("admin@example.com")
                .passwordHash("hash").role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        lenient().when(userScope.currentUser()).thenReturn(user);
    }

    private IssueReportRequest request(ReportType type, Severity severity) {
        return new IssueReportRequest(type, "Save button does nothing", "Clicked save, nothing happened",
                severity, "/tasks", "4.0.0");
    }

    private IssueReport stored(ReportType type, IssueReportStatus status, String resolutionNote) {
        IssueReport report = IssueReport.builder().id(10L).code("IR-001").user(user).reportType(type)
                .title("Save button does nothing").severity(Severity.HIGH).status(status)
                .resolutionNote(resolutionNote).archived(false).build();
        report.setCreatedAt(Instant.now());
        report.setUpdatedAt(Instant.now());
        return report;
    }

    @Test
    void creatingBugWithoutSeverityThrows() {
        assertThatThrownBy(() -> service.createReport(request(ReportType.BUG, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A bug report must include a severity.");
    }

    @Test
    void creatingBugWithSeverityOpensReportWithGeneratedCode() {
        when(issueReportRepository.findByUser_Id(1L)).thenReturn(List.of());
        when(issueReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IssueReportResponse response = service.createReport(request(ReportType.BUG, Severity.HIGH));

        assertThat(response.status()).isEqualTo(IssueReportStatus.OPEN);
        assertThat(response.code()).isEqualTo("IR-001");
        assertThat(response.reporterEmail()).isEqualTo("test@example.com");
    }

    @Test
    void creatingImprovementWithoutSeveritySucceeds() {
        when(issueReportRepository.findByUser_Id(1L)).thenReturn(List.of());
        when(issueReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IssueReportResponse response = service.createReport(request(ReportType.IMPROVEMENT, null));

        assertThat(response.status()).isEqualTo(IssueReportStatus.OPEN);
        assertThat(response.severity()).isNull();
    }

    @Test
    void resolvingBugWithoutResolutionNoteThrows() {
        when(userScope.currentUser()).thenReturn(admin);
        when(issueReportRepository.findById(10L)).thenReturn(Optional.of(stored(ReportType.BUG, IssueReportStatus.IN_PROGRESS, null)));

        assertThatThrownBy(() -> service.updateStatus(10L, "RESOLVED", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A resolved bug must include a resolution note.");
    }

    @Test
    void resolvingBugWithResolutionNoteSucceeds() {
        when(userScope.currentUser()).thenReturn(admin);
        when(issueReportRepository.findById(10L)).thenReturn(Optional.of(stored(ReportType.BUG, IssueReportStatus.IN_PROGRESS, null)));

        IssueReportResponse response = service.updateStatus(10L, "RESOLVED", "Fixed the click handler in 4.0.1");

        assertThat(response.status()).isEqualTo(IssueReportStatus.RESOLVED);
        assertThat(response.resolutionNote()).isEqualTo("Fixed the click handler in 4.0.1");
    }

    @Test
    void nonAdminCannotListWholeQueue() {
        assertThatThrownBy(() -> service.listAllReports(null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAdminCannotChangeStatus() {
        assertThatThrownBy(() -> service.updateStatus(10L, "IN_REVIEW", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userCannotReadAnotherUsersReport() {
        IssueReport othersReport = stored(ReportType.BUG, IssueReportStatus.OPEN, null);
        othersReport.setUser(admin); // owned by someone else
        when(issueReportRepository.findById(10L)).thenReturn(Optional.of(othersReport));

        assertThatThrownBy(() -> service.getReport(10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminCanReadAnyReport() {
        when(userScope.currentUser()).thenReturn(admin);
        IssueReport usersReport = stored(ReportType.BUG, IssueReportStatus.OPEN, null);
        when(issueReportRepository.findById(10L)).thenReturn(Optional.of(usersReport));

        assertThat(service.getReport(10L).id()).isEqualTo(10L);
    }

    @Test
    void adminFilterByStatusReturnsOnlyMatches() {
        when(userScope.currentUser()).thenReturn(admin);
        IssueReport open = stored(ReportType.BUG, IssueReportStatus.OPEN, null);
        IssueReport resolved = stored(ReportType.BUG, IssueReportStatus.RESOLVED, "done");
        resolved.setId(11L);
        when(issueReportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(open, resolved));

        List<IssueReportResponse> result = service.listAllReports(null, "RESOLVED", null);

        assertThat(result).extracting(IssueReportResponse::id).containsExactly(11L);
    }
}
