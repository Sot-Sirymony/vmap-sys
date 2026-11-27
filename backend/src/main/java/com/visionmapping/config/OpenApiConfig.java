package com.visionmapping.config;

import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.web.ApiPaths;
import com.visionmapping.web.RequiresStepUp;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * The OpenAPI description behind Swagger UI ({@code /swagger-ui.html}). Every
 * endpoint is marked as needing the JWT from {@code POST /api/auth/login}: use
 * "Authorize" in Swagger UI and paste the {@code token} value (without
 * "Bearer"). The public endpoints are marked as needing none.
 *
 * <p>V33 (D-33-16): the answers every endpoint can give (400, 401, 403, 429 and
 * 500, as an RFC 9457 problem) are added here once, not copied onto each
 * method, and an endpoint marked {@link RequiresStepUp} gets its
 * {@code X-Step-Up} header.
 *
 * <p>springdoc is on only in the local and test profiles, or with
 * {@code SWAGGER_ENABLED=true}; see {@code application.yml}.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
    static final String PROBLEM_SCHEMA = "Problem";
    static final String PROBLEM_MEDIA_TYPE = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    /** The answers any endpoint can give, added to an operation that does not declare them itself. */
    static final Map<String, String> COMMON_ANSWERS = commonAnswers();

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    /** Paths that need no token: the health check, the public sign-in paths, shared plans and shared reports. */
    static final List<PathPattern> PUBLIC_PATTERNS = Stream.concat(
                    Stream.of(ApiPaths.HEALTH),
                    Arrays.stream(ApiPaths.underBothPrefixes(Stream.concat(Arrays.stream(SecurityConfig.PUBLIC_API_PATHS),
                            Stream.of("/shared-plans/*", "/public/reports/*")).toArray(String[]::new))))
            .map(PARSER::parse)
            .toList();

    @Bean
    public OpenAPI visionMappingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vision Mapping Management System API")
                        .version("v1")
                        .description("""
                                Turns a dream into a plan: Vision Area → Dream → Goal → Step → Task, \
                                with partners, communication, reviews and progress. Every record is \
                                scoped to the signed-in user. Sign in with POST /api/auth/login, then \
                                use Authorize and paste the token. Errors are RFC 9457 problems \
                                (application/problem+json)."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("The access token from POST /api/auth/login or /api/auth/refresh.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /** V33 (D-33-16): the common error answers on every operation, and no token on the public ones. */
    @Bean
    public OpenApiCustomizer commonProblemAnswers() {
        return openApi -> {
            // Added here, after springdoc has built its own schemas, which replace any set on the bean.
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                boolean open = isPublic(path);
                for (Operation operation : item.readOperations()) {
                    addCommonAnswers(operation);
                    if (open) {
                        operation.setSecurity(List.of());
                    }
                }
            });
        };
    }

    /** V33 (D-33-12): the "Verify it's you" headers on endpoints marked {@link RequiresStepUp}. */
    @Bean
    public OperationCustomizer stepUpHeaders() {
        return (operation, handlerMethod) -> {
            RequiresStepUp marker = handlerMethod.getMethodAnnotation(RequiresStepUp.class);
            if (marker != null) {
                operation.addParametersItem(new HeaderParameter().name(StepUpService.HEADER).required(false)
                        .description("The id of a verified \"Verify it's you\" code. Without it the answer is 403 "
                                + "with code STEP_UP_REQUIRED.")
                        .schema(new StringSchema()));
                if (marker.confirm()) {
                    operation.addParametersItem(new HeaderParameter().name(StepUpService.CONFIRM_HEADER).required(false)
                            .description("The confirmation this action asks for.")
                            .schema(new StringSchema()));
                }
            }
            return operation;
        };
    }

    static boolean isPublic(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return PUBLIC_PATTERNS.stream().anyMatch(pattern -> pattern.matches(container));
    }

    static void addCommonAnswers(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        COMMON_ANSWERS.forEach((status, description) -> operation.getResponses().computeIfAbsent(status,
                code -> new ApiResponse().description(description)
                        .content(new Content().addMediaType(PROBLEM_MEDIA_TYPE,
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))))));
    }

    private static Map<String, String> commonAnswers() {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("400", "The request is not valid; errors lists the fields");
        answers.put("401", "Sign in first, or sign in again");
        answers.put("403", "Not allowed, or a \"Verify it's you\" code is needed (code STEP_UP_REQUIRED)");
        answers.put("429", "Too many requests; try again after Retry-After seconds");
        answers.put("500", "Server error; quote the reference when reporting it");
        return Collections.unmodifiableMap(answers);
    }

    /** RFC 9457 fields, the VMS extensions, and the version 1 compatibility fields (D-33-13). */
    @SuppressWarnings("rawtypes")
    private static Schema problemSchema() {
        ObjectSchema field = new ObjectSchema();
        field.addProperty("field", new StringSchema());
        field.addProperty("message", new StringSchema());
        ObjectSchema problem = new ObjectSchema();
        problem.description("An RFC 9457 problem, sent as application/problem+json.");
        problem.addProperty("type", new StringSchema().format("uri")
                .description("about:blank, or urn:vms:problem:<code> when there is a VMS code"));
        problem.addProperty("title", new StringSchema().description("The HTTP status name"));
        problem.addProperty("status", new IntegerSchema());
        problem.addProperty("detail", new StringSchema().description("A message safe to show the user"));
        problem.addProperty("instance", new StringSchema().format("uri-reference").description("The request path"));
        problem.addProperty("reference", new StringSchema().description("Matches the server log line for this error"));
        problem.addProperty("timestamp", new StringSchema().format("date-time"));
        problem.addProperty("code", new StringSchema().description("What happened, for a page that acts on it"));
        problem.addProperty("triesLeft", new IntegerSchema().description("Tries left after a wrong code"));
        problem.addProperty("action", new StringSchema().description("On STEP_UP_REQUIRED, which code to ask for"));
        problem.addProperty("errors", new ArraySchema().items(field).description("The fields that failed validation"));
        problem.addProperty("message", new StringSchema().description("Version 1 only: the same text as detail"));
        problem.addProperty("error", new StringSchema().description("Version 1 only: the same text as title"));
        problem.addProperty("path", new StringSchema().description("Version 1 only: the same text as instance"));
        return problem;
    }
}
