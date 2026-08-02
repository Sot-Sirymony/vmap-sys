package com.visionmapping.service.support;

import com.visionmapping.exception.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiting for password reset requests.
 *
 * Two independent limits, because they stop different things. The per-address
 * limit stops the endpoint being used to flood one person's inbox — the request
 * is anonymous, so without it anyone can mail a stranger as often as they like.
 * The per-IP limit stops one caller working through a list of addresses, which
 * is how the endpoint would be probed for which addresses exist.
 *
 * <p><b>Checked before the account lookup, always.</b> A limit applied only to
 * addresses that have accounts would answer differently for registered and
 * unregistered addresses, which is precisely the disclosure the 204-either-way
 * response exists to prevent.
 *
 * <p>In memory, so the limits are per instance rather than per cluster. That is
 * honest for the current single-instance deployment; behind more than one
 * replica the effective limit multiplies by the replica count and this should
 * move to Redis, which the app already runs.
 *
 * <p>The table is bounded. This instance has 512MB and was being OOM-killed
 * recently, so an unbounded map keyed by attacker-supplied strings is not
 * acceptable: past the cap, expired entries are purged and — if that does not
 * free anything — further distinct keys are refused rather than stored. Failing
 * closed is deliberate. A caller being told to retry is recoverable; the process
 * being killed takes the whole service down.
 */
@Slf4j
@Component
public class PasswordResetRateLimiter {

    /** ~1MB at worst, which the instance can afford. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Value("${app.password-reset.rate-limit.per-email:3}")
    private int perEmailLimit;

    @Value("${app.password-reset.rate-limit.per-email-window-minutes:15}")
    private long perEmailWindowMinutes;

    @Value("${app.password-reset.rate-limit.per-ip:20}")
    private int perIpLimit;

    @Value("${app.password-reset.rate-limit.per-ip-window-minutes:60}")
    private long perIpWindowMinutes;

    /**
     * @throws TooManyRequestsException if either limit is exhausted
     */
    public void check(String email, String clientIp) {
        // The address first: it is the limit a victim depends on, and checking it
        // before the IP means a distributed flood against one inbox still trips.
        consume("email:" + email, perEmailLimit, Duration.ofMinutes(perEmailWindowMinutes),
                "Too many reset requests for this address. Try again later.");
        if (clientIp != null && !clientIp.isBlank()) {
            consume("ip:" + clientIp, perIpLimit, Duration.ofMinutes(perIpWindowMinutes),
                    "Too many reset requests from this network. Try again later.");
        }
    }

    private void consume(String key, int limit, Duration window, String message) {
        Instant now = Instant.now();

        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt.isBefore(now)) {
                if (existing == null && windows.size() >= MAX_TRACKED_KEYS && !purgeExpired(now)) {
                    return null; // signals "no capacity" — handled below
                }
                return new Window(now.plus(window), new AtomicInteger(0));
            }
            return existing;
        });

        if (current == null) {
            log.warn("Password reset rate-limit table is full; refusing new key");
            throw new TooManyRequestsException(message);
        }

        if (current.count.incrementAndGet() > limit) {
            throw new TooManyRequestsException(message);
        }
    }

    /** @return true if anything was freed */
    private boolean purgeExpired(Instant now) {
        int before = windows.size();
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt.isBefore(now)) {
                iterator.remove();
            }
        }
        return windows.size() < before;
    }

    private record Window(Instant expiresAt, AtomicInteger count) {
    }
}
