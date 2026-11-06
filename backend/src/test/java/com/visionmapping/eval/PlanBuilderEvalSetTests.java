package com.visionmapping.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** V11 (PB-NFR-08): the sample set stays as agreed: 20 dreams, 10 English, 5 Khmer, 5 vague. */
class PlanBuilderEvalSetTests {

    @Test
    void theSampleSetIsComplete() throws Exception {
        PlanBuilderEvalSet set = PlanBuilderEvalSet.load();
        assertThat(set.dreams()).hasSize(20);
        assertThat(set.dreams().stream().map(PlanBuilderEvalSet.Dream::id).collect(Collectors.toSet())).hasSize(20);
        assertThat(set.dreams().stream().filter(dream -> !dream.vague() && dream.language().equals("en"))).hasSize(10);
        assertThat(set.dreams().stream().filter(dream -> !dream.vague() && dream.language().equals("km"))).hasSize(5);
        assertThat(set.dreams().stream().filter(PlanBuilderEvalSet.Dream::vague)).hasSize(5);
        set.dreams().forEach(dream -> {
            assertThat(dream.title()).as(dream.id()).isNotBlank();
            if (dream.vague()) {
                assertThat(dream.whyImportant()).as(dream.id() + " is meant to be vague").isBlank();
            } else {
                assertThat(dream.whyImportant()).as(dream.id()).isNotBlank();
                assertThat(dream.months()).as(dream.id()).isPositive();
            }
            if (dream.language().equals("km")) {
                assertThat(dream.title()).as(dream.id() + " is written in Khmer").containsPattern("[\\u1780-\\u17FF]");
            }
        });
    }
}
