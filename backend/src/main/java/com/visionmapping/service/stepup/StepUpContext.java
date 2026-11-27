package com.visionmapping.service.stepup;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * V33 (D-33-12): the "Verify it's you" headers of the current request, read once
 * by {@code StepUpContextInterceptor} so that controllers no longer repeat
 * {@code @RequestHeader(StepUpService.HEADER)} on every method.
 *
 * <p>This only carries the headers. {@link StepUpService} still decides, with
 * the action and its target (BR-33-6).
 *
 * @param challenge the {@value StepUpService#HEADER} header: the id of a verified code
 * @param confirm   the {@value StepUpService#CONFIRM_HEADER} header
 */
public record StepUpContext(@Nullable String challenge, @Nullable String confirm) {

    static final String ATTRIBUTE = StepUpContext.class.getName();

    private static final StepUpContext NONE = new StepUpContext(null, null);

    /** Reads the headers of a request. */
    public static StepUpContext of(HttpServletRequest request) {
        return new StepUpContext(request.getHeader(StepUpService.HEADER), request.getHeader(StepUpService.CONFIRM_HEADER));
    }

    /** Keeps the headers on the request for the rest of its handling. */
    public static void store(HttpServletRequest request, StepUpContext context) {
        request.setAttribute(ATTRIBUTE, context);
    }

    /**
     * The headers of the request being handled: as stored by the interceptor,
     * else read from the request, else none (outside a web request, for example
     * in a scheduled job).
     */
    public static StepUpContext current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return NONE;
        }
        if (attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof StepUpContext stored) {
            return stored;
        }
        if (attributes instanceof ServletRequestAttributes servlet) {
            return of(servlet.getRequest());
        }
        return NONE;
    }
}
