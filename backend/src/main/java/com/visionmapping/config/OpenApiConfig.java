package com.visionmapping.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI description behind Swagger UI ({@code /swagger-ui.html}). Every
 * endpoint is marked as needing the JWT from {@code POST /api/auth/login}: use
 * "Authorize" in Swagger UI and paste the {@code token} value (without
 * "Bearer"). The few public endpoints still work without it.
 *
 * <p>springdoc is on only in the local and test profiles, or with
 * {@code SWAGGER_ENABLED=true}; see {@code application.yml}.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

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
                                use Authorize and paste the token."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("The access token from POST /api/auth/login or /api/auth/refresh.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
