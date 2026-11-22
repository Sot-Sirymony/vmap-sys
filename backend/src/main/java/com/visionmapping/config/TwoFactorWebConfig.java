package com.visionmapping.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** V21 (TF-FR-34): registers the admin and coach setup check on every API call. */
@Configuration
@RequiredArgsConstructor
public class TwoFactorWebConfig implements WebMvcConfigurer {

    private final TwoFactorSetupInterceptor twoFactorSetupInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(twoFactorSetupInterceptor).addPathPatterns("/api/**");
    }
}
