package com.visionmapping.security.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * V10 (SEC-09-01, SEC-08-06): records a security event each time the method
 * runs: SUCCESS when it returns, FAILURE when it throws. The target is the
 * first Long argument, or else the id() of the returned record. The detail is
 * the method name, so "createResource" and "deleteResource" stay apart.
 *
 * <p>Only calls through the Spring proxy are recorded, so a service calling
 * its own method is not counted twice.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurityAudit {

    SecurityEventType value();

    /** What the target id refers to, such as "cohort" or "user". */
    String target() default "";
}
