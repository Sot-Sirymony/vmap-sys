package com.visionmapping.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.dto.response.LearningModuleDetailResponse.Field;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.WorkStyleArchetype;
import com.visionmapping.service.PageSwitches;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** V15: the small rules the page helpers check Claude's answers against. */
class PageHelperRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @Test
    void aNextTaskDateMustFallBetweenTodayAndTheStepsDate() {
        LocalDate latest = TODAY.plusDays(10);
        assertThat(ObstacleHelpService.dueDate("2026-11-12", TODAY, latest)).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(ObstacleHelpService.dueDate("2026-11-08", TODAY, latest)).isNull();
        assertThat(ObstacleHelpService.dueDate("2026-12-30", TODAY, latest)).isNull();
        assertThat(ObstacleHelpService.dueDate("2026-12-30", TODAY, null)).isEqualTo(LocalDate.of(2026, 12, 30));
        assertThat(ObstacleHelpService.dueDate("next week", TODAY, latest)).isNull();
        assertThat(ObstacleHelpService.dueDate(" ", TODAY, latest)).isNull();
        assertThat(ObstacleHelpService.dueDate(null, TODAY, latest)).isNull();
    }

    @Test
    void anUnknownPriorityBecomesMedium() {
        assertThat(ObstacleHelpService.priority("high")).isEqualTo("HIGH");
        assertThat(ObstacleHelpService.priority(" critical ")).isEqualTo("CRITICAL");
        assertThat(ObstacleHelpService.priority("urgent")).isEqualTo("MEDIUM");
        assertThat(ObstacleHelpService.priority(null)).isEqualTo("MEDIUM");
    }

    @Test
    void aPracticeActionMustBeOneOfTheProgramsPages() {
        assertThat(WorkStyleHelpService.action("undated_steps")).isEqualTo("UNDATED_STEPS");
        assertThat(WorkStyleHelpService.action(" today ")).isEqualTo("TODAY");
        assertThat(WorkStyleHelpService.action("PHONE")).isNull();
        assertThat(WorkStyleHelpService.action(null)).isNull();
    }

    @Test
    void theWorkStyleReachesOtherHelpersOnlyWhenAllowedAndOnlyAsTheStyle() {
        AppUser user = AppUser.builder().fullName("Test").email("t@example.com").build();
        assertThat(WorkStyleContext.line(user)).isEmpty();
        user.setAiUseWorkStyle(true);
        assertThat(WorkStyleContext.line(user)).as("no result yet").isEmpty();
        user.setWorkStyleDominant(WorkStyleArchetype.PLANNER);
        user.setWorkStylePaceFastScore(2);
        assertThat(WorkStyleContext.line(user)).contains("PLANNER").doesNotContain("with some", "2");
        user.setWorkStyleSecondary(WorkStyleArchetype.STEADIER);
        assertThat(WorkStyleContext.line(user)).contains("with some STEADIER");
        user.setAiUseWorkStyle(false);
        assertThat(WorkStyleContext.line(user)).isEmpty();
    }

    @Test
    void refiningKeepsTheParticipantsOwnRowsAsTheyWroteThem() {
        List<Field> fields = List.of(new Field("belief", "Belief", "text"), new Field("replacement", "Replacement", "text"));
        List<Map<String, String>> drafted = List.of(Map.of("belief", "Claude one", "replacement", "R1"));
        List<Map<String, String>> typed = new ArrayList<>(Arrays.asList(Map.of("belief", "Mine"), null, Map.of("belief", "Third")));

        List<Map<String, String>> kept = AiModuleService.keepRows(drafted, typed, Arrays.asList(0, 2, 5, -1, null), fields);
        assertThat(kept).hasSize(3);
        assertThat(kept.get(0)).containsEntry("belief", "Mine").containsEntry("replacement", "");
        assertThat(kept.get(1)).containsEntry("belief", "");
        assertThat(kept.get(2)).containsEntry("belief", "Third");
        assertThat(AiModuleService.keepRows(drafted, null, List.of(0), fields)).isEqualTo(drafted);
        assertThat(AiModuleService.keepRows(drafted, typed, List.of(1), fields).get(1)).containsEntry("belief", "");
    }

    @Test
    void pageSwitchesAreStoredSortedWithoutDuplicates() {
        assertThat(PageSwitches.join(Arrays.asList("WORK_STYLE", " OBSTACLES ", "WORK_STYLE", "", null))).isEqualTo("OBSTACLES,WORK_STYLE");
        assertThat(PageSwitches.split("OBSTACLES, WORK_STYLE,")).containsExactly("OBSTACLES", "WORK_STYLE");
        assertThat(PageSwitches.split("")).isEmpty();
        assertThat(PageSwitches.split(null)).isEmpty();
    }
}
