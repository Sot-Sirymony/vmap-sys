package com.visionmapping.web;

import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

/**
 * V33 (D-33-2): registers {@link LegacyApiPathFilter} ahead of Spring Security,
 * so that a forwarded request passes the same checks as one sent to
 * {@code /api/v1} directly (BR-33-7).
 */
@Configuration
public class ApiVersionConfig {

    /** Before Spring Security (-100) and the request context filter (-105). */
    static final int LEGACY_FILTER_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER - 50;

    @Bean
    public FilterRegistrationBean<LegacyApiPathFilter> legacyApiPathFilter(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMapping,
            @Value("${app.api.legacy-sunset:}") String sunset) {
        SingletonSupplier<List<PathPattern>> patterns = SingletonSupplier.of(() -> versionOnePatterns(handlerMapping.getObject()));
        FilterRegistrationBean<LegacyApiPathFilter> registration =
                new FilterRegistrationBean<>(new LegacyApiPathFilter(patterns, sunset));
        registration.setOrder(LEGACY_FILTER_ORDER);
        registration.addUrlPatterns(ApiPaths.LEGACY + "/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.FORWARD);
        return registration;
    }

    /** The path patterns of every handler mapped under {@code /api/v1}. */
    static List<PathPattern> versionOnePatterns(RequestMappingHandlerMapping handlerMapping) {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .map(RequestMappingInfo::getPathPatternsCondition)
                .filter(Objects::nonNull)
                .flatMap(condition -> condition.getPatterns().stream())
                .filter(pattern -> pattern.getPatternString().startsWith(ApiPaths.V1 + "/"))
                .toList();
    }
}
