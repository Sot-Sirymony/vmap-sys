package com.visionmapping.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** V15 (AH-NFR-12): the fixed cases in src/test/resources/page-assistant-eval/cases.json. */
record PageAssistantEvalSet(String about, List<Case> cases) {

    /** One case. Only the fields its page uses are set. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Case(String id, String page, String language, String kind, Integer moduleNumber, List<Map<String, String>> rows,
                List<Integer> scores, String question, Map<String, String> obstacle, String mode, Map<String, String> partner,
                Map<String, String> message, List<Integer> counts, List<String> mustNot) {
    }

    static PageAssistantEvalSet load() throws IOException {
        try (InputStream in = PageAssistantEvalSet.class.getResourceAsStream("/page-assistant-eval/cases.json")) {
            return new ObjectMapper().readValue(in, PageAssistantEvalSet.class);
        }
    }
}
