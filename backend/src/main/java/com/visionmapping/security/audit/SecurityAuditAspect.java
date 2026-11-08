package com.visionmapping.security.audit;

import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Records {@link SecurityAudit} methods. Ordered before the transaction
 * advice, so it wraps the commit: a change that fails while saving is
 * recorded as FAILURE, not SUCCESS.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class SecurityAuditAspect {

    private final SecurityEventService events;

    @Around("@annotation(audit)")
    public Object record(ProceedingJoinPoint call, SecurityAudit audit) throws Throwable {
        String action = call.getSignature().getName();
        String target = audit.target().isEmpty() ? null : audit.target();
        Object result;
        try {
            result = call.proceed();
        } catch (AccessDeniedException denied) {
            // Recorded once, as ACCESS_DENIED, by the exception handler.
            throw denied;
        } catch (Throwable failure) {
            // Broad on purpose: any failure of the audited call is logged, then
            // rethrown unchanged, so callers see exactly what they would without the aspect.
            events.record(audit.value(), SecurityOutcome.FAILURE, target, idFrom(call.getArgs(), null),
                    action + " failed: " + failure.getClass().getSimpleName());
            throw failure;
        }
        events.record(audit.value(), SecurityOutcome.SUCCESS, target, idFrom(call.getArgs(), result), action);
        return result;
    }

    @Nullable
    private static Long idFrom(Object[] args, Object result) {
        for (Object arg : args) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        if (result != null && result.getClass().isRecord()) {
            try {
                Method id = result.getClass().getMethod("id");
                Object value = id.invoke(result);
                return value instanceof Number number ? number.longValue() : null;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }
}
