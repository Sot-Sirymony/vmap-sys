package com.visionmapping.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** V11 (PB-NFR-08): the fixed sample dreams in src/test/resources/plan-builder-eval/dreams.json. */
record PlanBuilderEvalSet(String about, List<Dream> dreams) {

    record Dream(String id, String language, boolean vague, String area, String title, String whyImportant,
                 String successDefinition, int months) {
    }

    static PlanBuilderEvalSet load() throws IOException {
        try (InputStream in = PlanBuilderEvalSet.class.getResourceAsStream("/plan-builder-eval/dreams.json")) {
            return new ObjectMapper().readValue(in, PlanBuilderEvalSet.class);
        }
    }
}
