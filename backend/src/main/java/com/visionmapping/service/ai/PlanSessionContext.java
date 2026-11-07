package com.visionmapping.service.ai;

/**
 * V11 (PB-FR-36): the Build my plan session the current request's AI call
 * belongs to. {@link PlanSessionGuard} sets it when it counts a request, and
 * every usage-log row made on the same thread carries it. Cleared at the end
 * of each HTTP request by {@link com.visionmapping.config.PlanSessionContextFilter},
 * so a pooled thread never carries a key into the next request. A streamed
 * chat answer finishes on another thread, so the Dream Coach passes the key
 * along itself.
 */
public final class PlanSessionContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private PlanSessionContext() {
    }

    static void set(String sessionKey) {
        CURRENT.set(sessionKey);
    }

    /** The session key for this thread's AI call, or null outside a session. */
    public static String current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
