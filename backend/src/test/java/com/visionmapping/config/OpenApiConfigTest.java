package com.visionmapping.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.web.RequiresStepUp;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

/** BRD 33 D-33-12, D-33-16: the OpenAPI customizers on their own. */
class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void aMarkedEndpointGetsTheStepUpHeaders() throws NoSuchMethodException {
        Operation plain = config.stepUpHeaders().customize(new Operation(), handler("plain"));
        Operation marked = config.stepUpHeaders().customize(new Operation(), handler("marked"));
        Operation confirmed = config.stepUpHeaders().customize(new Operation(), handler("confirmed"));

        assertThat(plain.getParameters()).isNull();
        assertThat(marked.getParameters()).extracting(Parameter::getName).containsExactly(StepUpService.HEADER);
        assertThat(confirmed.getParameters()).extracting(Parameter::getName)
                .containsExactly(StepUpService.HEADER, StepUpService.CONFIRM_HEADER);
        assertThat(confirmed.getParameters()).allMatch(parameter -> "header".equals(parameter.getIn()));
    }

    @Test
    void theCommonAnswersAreAddedWithoutReplacingTheOperationsOwn() {
        Operation operation = new Operation();
        OpenApiConfig.addCommonAnswers(operation);
        operation.getResponses().get("400").setDescription("Own text");
        OpenApiConfig.addCommonAnswers(operation);

        assertThat(operation.getResponses()).containsKeys("400", "401", "403", "429", "500");
        assertThat(operation.getResponses().get("400").getDescription()).isEqualTo("Own text");
    }

    @Test
    void aDocumentWithNoPathsOrComponentsStillGetsTheProblemSchema() {
        OpenAPI empty = new OpenAPI();
        config.commonProblemAnswers().customise(empty);

        assertThat(empty.getComponents().getSchemas()).containsKey(OpenApiConfig.PROBLEM_SCHEMA);
    }

    @Test
    void onlyPublicPathsAreOpened() {
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/auth/login", new PathItem().post(new Operation()))
                .addPathItem("/api/v1/goals", new PathItem().get(new Operation())));
        config.commonProblemAnswers().customise(openApi);

        assertThat(openApi.getPaths().get("/api/v1/auth/login").getPost().getSecurity()).isEmpty();
        assertThat(openApi.getPaths().get("/api/v1/goals").getGet().getSecurity()).isNull();
        assertThat(OpenApiConfig.isPublic("/api/health")).isTrue();
        assertThat(OpenApiConfig.isPublic("/api/v1/public/reports/{token}")).isTrue();
        assertThat(OpenApiConfig.isPublic("/api/v1/admin/users")).isFalse();
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(name));
    }

    /** Stand-in handlers. */
    public static class Endpoints {

        public void plain() {
            // No step-up.
        }

        @RequiresStepUp
        public void marked() {
            // Asks for a code.
        }

        @RequiresStepUp(confirm = true)
        public void confirmed() {
            // Asks for a code and a confirmation.
        }
    }
}
