package com.visionmapping.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * V33 (D-33-12): marks an endpoint whose service asks for a "Verify it's you"
 * code. It documents the endpoint, adds the {@code X-Step-Up} header to the
 * OpenAPI description and lets {@code ControllerRulesTest} find it.
 *
 * <p>It does not check anything by itself: the service calls
 * {@code StepUpService.requireFromRequest(action)}, which reads the code the
 * interceptor kept in {@code StepUpContext} (BR-33-6).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresStepUp {

    /** Whether the endpoint also reads {@code X-Step-Up-Confirm}. */
    boolean confirm() default false;
}
