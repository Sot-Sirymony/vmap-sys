package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.GoalSynergyLinkRequest;
import com.visionmapping.dto.response.GoalSynergyLinkResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.GoalSynergyLink;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.GoalSynergyLinkRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FR-35 / BR-29: synergy links join two distinct goals of the same user, reject
 * self-links and duplicate pairs, and are read from either side. Cross-area
 * links carry the crossVisionArea flag; archived goals drop out of the listing.
 */
@ExtendWith(MockitoExtension.class)
class GoalSynergyLinkServiceTest {

    @Mock private EntityLookup lookup;
    @Mock private GoalSynergyLinkRepository repository;

    private GoalSynergyLinkService service;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        service = new GoalSynergyLinkService(lookup, repository);
        testUser = AppUser.builder().id(1L).fullName("Test User").email("t@example.com").passwordHash("h").build();
        lenient().when(lookup.userId()).thenReturn(1L);
        lenient().when(lookup.currentUser()).thenReturn(testUser);
    }

    private Goal goal(long id, long areaId, boolean archived) {
        VisionArea area = VisionArea.builder().id(areaId).name("Area " + areaId).build();
        Dream dream = Dream.builder().id(100 + id).visionArea(area).build();
        return Goal.builder().id(id).code("G-" + id).title("Goal " + id).dream(dream).user(testUser).archived(archived).build();
    }

    private GoalSynergyLink link(long id, Goal a, Goal b) {
        return GoalSynergyLink.builder().id(id).user(testUser).goal(a).relatedGoal(b).note("reinforces").build();
    }

    @Test
    void linkingAGoalToItselfIsRejected() {
        when(lookup.goal(5L)).thenReturn(goal(5, 10, false));

        assertThatThrownBy(() -> service.createLink(5L, new GoalSynergyLinkRequest(5L, "x")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A goal cannot be linked to itself.");
    }

    @Test
    void linkingAnAlreadyLinkedPairIsRejected() {
        when(lookup.goal(5L)).thenReturn(goal(5, 10, false));
        when(lookup.goal(8L)).thenReturn(goal(8, 20, false));
        when(repository.existsByUser_IdAndGoal_IdAndRelatedGoal_Id(1L, 5L, 8L)).thenReturn(true);

        assertThatThrownBy(() -> service.createLink(5L, new GoalSynergyLinkRequest(8L, "x")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("These goals are already linked.");
    }

    @Test
    void creatingACrossAreaLinkSucceedsAndFlagsIt() {
        when(lookup.goal(5L)).thenReturn(goal(5, 10, false));
        when(lookup.goal(8L)).thenReturn(goal(8, 20, false)); // different vision area
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GoalSynergyLinkResponse response = service.createLink(5L, new GoalSynergyLinkRequest(8L, "career funds health"));

        assertThat(response.goalId()).isEqualTo(5L);
        assertThat(response.relatedGoalId()).isEqualTo(8L);
        assertThat(response.crossVisionArea()).isTrue();
        assertThat(response.note()).isEqualTo("career funds health");
    }

    @Test
    void linksAreReadFromEitherSideOfThePair() {
        Goal viewed = goal(5, 10, false);
        when(lookup.goal(5L)).thenReturn(viewed);
        // One link stored as (5,8), another stored as (3,5) — both touch goal 5.
        when(repository.findForGoal(1L, 5L)).thenReturn(List.of(
                link(1L, viewed, goal(8, 20, false)),
                link(2L, goal(3, 10, false), viewed)));

        List<GoalSynergyLinkResponse> links = service.listLinks(5L);

        assertThat(links).extracting(GoalSynergyLinkResponse::relatedGoalId).containsExactlyInAnyOrder(8L, 3L);
    }

    @Test
    void linksToArchivedGoalsAreHidden() {
        Goal viewed = goal(5, 10, false);
        when(lookup.goal(5L)).thenReturn(viewed);
        when(repository.findForGoal(1L, 5L)).thenReturn(List.of(
                link(1L, viewed, goal(8, 20, false)),
                link(2L, viewed, goal(9, 20, true)))); // goal 9 archived → hidden

        List<GoalSynergyLinkResponse> links = service.listLinks(5L);

        assertThat(links).extracting(GoalSynergyLinkResponse::relatedGoalId).containsExactly(8L);
    }
}
