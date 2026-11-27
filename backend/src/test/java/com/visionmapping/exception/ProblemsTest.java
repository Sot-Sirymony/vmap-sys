package com.visionmapping.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

/** BRD 33 D-33-13: the shape of every error answer. */
class ProblemsTest {

    @Test
    void aPlainProblemHasTheStandardFieldsAndTheVersionOneOnes() {
        ProblemDetail problem = Problems.of(HttpStatus.NOT_FOUND, "Goal not found.", "/api/v1/goals/9");

        assertThat(problem.getType()).isEqualTo(URI.create("about:blank"));
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("Goal not found.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/goals/9"));
        assertThat(problem.getProperties())
                .containsEntry("message", "Goal not found.")
                .containsEntry("error", "Not Found")
                .containsEntry("path", "/api/v1/goals/9")
                .containsKey("timestamp")
                .doesNotContainKeys("code", "triesLeft", "action");
        assertThat(Problems.reference(problem)).matches("[0-9a-f]{12}");
    }

    @Test
    void aCodeBecomesAVmsProblemType() {
        ProblemDetail problem = Problems.of(HttpStatus.FORBIDDEN, "Enter your code.", "/api/account/export",
                "STEP_UP_REQUIRED", 2, "TOTP");

        assertThat(problem.getType()).isEqualTo(URI.create("urn:vms:problem:step-up-required"));
        assertThat(problem.getProperties())
                .containsEntry("code", "STEP_UP_REQUIRED")
                .containsEntry("triesLeft", 2)
                .containsEntry("action", "TOTP");
    }

    @Test
    void aPathThatIsNotAUriLeavesNoInstance() {
        assertThat(Problems.of(HttpStatus.BAD_REQUEST, "Bad.", "/a b|c").getInstance()).isNull();
        assertThat(Problems.of(HttpStatus.BAD_REQUEST, "Bad.", "").getInstance()).isNull();
        assertThat(Problems.instanceOf(null)).isNull();
    }

    @Test
    void validationListsEveryField() {
        ProblemDetail problem = Problems.invalid("name: must not be blank", "/api/v1/goals",
                List.of(new Problems.FieldProblem("name", "must not be blank")));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("errors", List.of(new Problems.FieldProblem("name", "must not be blank")));
    }

    @Test
    void theAnswerIsProblemJsonWithTheProblemsStatus() {
        ResponseEntity<ProblemDetail> answer = Problems.answer(Problems.of(HttpStatus.CONFLICT, "Changed.", "/x"));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(answer.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void aProblemWithoutPropertiesHasNoReference() {
        assertThat(Problems.reference(ProblemDetail.forStatus(HttpStatus.BAD_REQUEST))).isEmpty();
    }

    /** The security filters write the same JSON as the global handler: extensions at the top level. */
    @Test
    void theWriterPutsTheExtensionsAtTheTopLevel() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        new ProblemWriter(mapper).write(response, Problems.of(HttpStatus.UNAUTHORIZED, "Authentication required.", "/api/v1/goals"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("detail").asText()).isEqualTo("Authentication required.");
        assertThat(body.get("reference").asText()).matches("[0-9a-f]{12}");
        assertThat(body.get("message").asText()).isEqualTo("Authentication required.");
        assertThat(body.has("properties")).isFalse();
    }
}
