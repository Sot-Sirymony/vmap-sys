package com.visionmapping.web;

import com.visionmapping.service.stepup.StepUpContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * V33 (D-33-12): reads the "Verify it's you" headers once per request into
 * {@link StepUpContext}. It never allows or refuses a request: the service
 * decides, because the check depends on the action and its target (BR-33-6).
 */
@Component
public class StepUpContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        StepUpContext.store(request, StepUpContext.of(request));
        return true;
    }
}
