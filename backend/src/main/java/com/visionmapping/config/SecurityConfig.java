package com.visionmapping.config;

import com.visionmapping.security.AppUserDetailsService;
import com.visionmapping.security.JwtAuthenticationFilter;
import com.visionmapping.security.RestAccessDeniedHandler;
import com.visionmapping.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    /**
     * Swagger UI is a real page: it loads its own scripts, styles and images and
     * calls the API from the same origin. Swagger UI sets inline styles, hence
     * 'unsafe-inline' for styles only; scripts stay 'self'. Still no framing.
     */
    static final String DOCS_CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                    + "frame-ancestors 'none'; base-uri 'none'; form-action 'self'";
    /** The OpenAPI description and Swagger UI; they answer 404 when springdoc is off. */
    static final String[] DOCS_PATHS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";
    static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppUserDetailsService appUserDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AppUserDetailsService appUserDetailsService,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            @Value("${app.cors.allowed-origins}") String corsAllowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.appUserDetailsService = appUserDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsAllowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // V10 (SEC-02-01, SEC-02-02). The API only ever answers with JSON or
                // file downloads, so its CSP allows nothing to load or run, and no
                // page may frame it. Spring already sends nosniff and no-cache.
                // HSTS is sent on every response: Render ends TLS at its proxy, so
                // the app sees plain HTTP and Spring's default (HTTPS only) would
                // never send it. Browsers ignore HSTS received over plain HTTP, so
                // local development is unaffected.
                .headers(headers -> headers
                        // The docs pages get their own policy; every other response the strict one.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(docsPaths()),
                                new ContentSecurityPolicyHeaderWriter(API_CONTENT_SECURITY_POLICY)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(docsPaths(),
                                new ContentSecurityPolicyHeaderWriter(DOCS_CONTENT_SECURITY_POLICY)))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .requestMatcher(AnyRequestMatcher.INSTANCE)
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS))
                )
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        // V9 (AI-NFR-01): a streamed Dream Coach reply finishes on an
                        // async dispatch, which the JWT filter does not run on. The
                        // request it continues was already authorised.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // The error page only renders the status of a request that
                        // already ran; blocking it turned real errors into a 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/register",
                                "/api/auth/login",
                                // V10 (SEC-07-04): called when the access token has
                                // run out; the refresh token in the body is the key.
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                // Recovery is for users who cannot sign in, so it
                                // cannot sit behind authentication. Both are
                                // rate-limited only by the token being unguessable
                                // and single-use.
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                // FR-20: interface text changes, needed before
                                // sign-in to draw the sign-in page. Read-only.
                                "/api/ui-text"
                        ).permitAll()
                        // The API description and Swagger UI, when springdoc is turned
                        // on (local and test profiles, or SWAGGER_ENABLED=true). Every
                        // call made from the UI still needs a token.
                        .requestMatchers(DOCS_PATHS).permitAll()
                        // V9 (v0.1 FR-10): a partner opens a shared plan without an
                        // account. Read-only; the 256-bit token in the path is the key.
                        .requestMatchers(HttpMethod.GET, "/api/shared-plans/*").permitAll()
                        // V10 (SEC-01-03): role rules by path, checked before any
                        // controller or request-body validation runs. Services check
                        // again through Roles. The role comes from the database on
                        // every request, so a role change applies at once.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/coach/**").hasAnyRole("COACH", "ADMIN")
                        .anyRequest().authenticated()
                )
                // Without these, Spring's default handling answers an anonymous
                // request with 403, which claims the caller is known and lacks a
                // permission. Both must be set: configuring only one makes Spring
                // fall back to it for the other case too, collapsing "sign in
                // again" and "you lack the role" into a single indistinguishable
                // answer.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(appUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static RequestMatcher docsPaths() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(Arrays.stream(DOCS_PATHS).map(paths::matcher).map(RequestMatcher.class::cast).toList());
    }
}
