package com.visionmapping.config;

import com.visionmapping.web.ApiPaths;
import com.visionmapping.web.StepUpContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * V21 (TF-FR-34): registers the admin and coach setup check on every API call.
 * V33 (D-33-12): and the interceptor that keeps the "Verify it's you" headers.
 */
@Configuration
@RequiredArgsConstructor
public class TwoFactorWebConfig implements WebMvcConfigurer {

    private final TwoFactorSetupInterceptor twoFactorSetupInterceptor;
    private final StepUpContextInterceptor stepUpContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(twoFactorSetupInterceptor).addPathPatterns(ApiPaths.LEGACY + "/**");
        registry.addInterceptor(stepUpContextInterceptor).addPathPatterns(ApiPaths.LEGACY + "/**");
    }
}
