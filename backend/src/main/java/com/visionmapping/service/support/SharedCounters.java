package com.visionmapping.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * ASVS F-04: rate-limit and sign-in lockout counts kept in Redis, so they
 * survive a backend restart or deploy and are shared if the backend ever runs
 * as more than one instance.
 *
 * <p>On when {@code app.security.counters.store} is {@code redis} (it follows
 * {@code CACHE_TYPE}, which render.yaml sets to {@code redis}). Otherwise, and
 * whenever Redis does not answer, every method throws {@link Unavailable} and
 * the limiters use their own in-memory counts, as they did before. A Redis
 * outage therefore never blocks sign-in; the limits just become per instance
 * again until it is back.
 *
 * <p>Keys are SHA-256 hashes of the limiter's key, so emails and IP addresses
 * are not stored in Redis in clear. Each counter is a fixed window: the first
 * increment sets the expiry, done atomically in one script.
 */
@Slf4j
@Component
public class SharedCounters {

    private static final String PREFIX = "vms:limit:";

    /** INCR, set the expiry on the first hit (or if it is missing), return {count, ms left}. */
    private static final DefaultRedisScript<List<Long>> HIT = new DefaultRedisScript<>("""
            local c = redis.call('INCR', KEYS[1])
            local t = redis.call('PTTL', KEYS[1])
            if c == 1 or t < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              t = tonumber(ARGV[1])
            end
            return {c, t}
            """, listOfLongs());

    /** Thrown when the shared store is off or not answering; the caller falls back to memory. */
    public static final class Unavailable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unavailable(String message, @Nullable Throwable cause) {
            super(message, cause, false, false);
        }
    }

    /** One counted attempt: the count in the window, and how long the window has left. */
    public record Hit(long count, Duration remaining) {
    }

    @Nullable
    private final StringRedisTemplate redis;
    private final AtomicBoolean down = new AtomicBoolean(false);

    @Autowired
    public SharedCounters(ObjectProvider<StringRedisTemplate> redis,
                          @Value("${app.security.counters.store:memory}") String store) {
        this("redis".equalsIgnoreCase(store) ? redis.getIfAvailable() : null);
    }

    SharedCounters(@Nullable StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Always in memory: for unit tests and for limiters built without Spring. */
    public static SharedCounters disabled() {
        return new SharedCounters((StringRedisTemplate) null);
    }

    public boolean enabled() {
        return redis != null;
    }

    /** Counts one attempt against {@code key} in a window of {@code window}. */
    public Hit hit(String key, Duration window) {
        List<Long> result = call(template -> template.execute(HIT, List.of(redisKey(key)),
                Long.toString(Math.max(1, window.toMillis()))));
        if (result == null || result.size() < 2) {
            throw unavailable("unexpected answer", null);
        }
        long count = result.get(0);
        long millis = result.get(1);
        return new Hit(count, Duration.ofMillis(Math.max(0, millis)));
    }

    /** Sets {@code key} for {@code duration}, for example a lock. */
    public void set(String key, Duration duration) {
        call(template -> {
            template.opsForValue().set(redisKey(key), "1", duration);
            return Boolean.TRUE;
        });
    }

    /** How long {@code key} has left, or empty when it is not set. */
    public Optional<Duration> remaining(String key) {
        Long millis = call(template -> template.getExpire(redisKey(key), TimeUnit.MILLISECONDS));
        if (millis == null || millis <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(millis));
    }

    public void delete(String... keys) {
        call(template -> template.delete(Arrays.stream(keys).map(SharedCounters::redisKey).toList()));
    }

    private <T> T call(Function<StringRedisTemplate, T> action) {
        if (redis == null) {
            throw new Unavailable("shared counters are off", null);
        }
        try {
            T value = action.apply(redis);
            if (down.compareAndSet(true, false)) {
                log.info("Shared rate-limit counters are back on Redis");
            }
            return value;
        } catch (RuntimeException exception) {
            throw unavailable(exception.getClass().getSimpleName(), exception);
        }
    }

    private Unavailable unavailable(String reason, @Nullable Throwable cause) {
        if (down.compareAndSet(false, true)) {
            log.warn("Redis is not answering for rate-limit counters ({}); using in-memory counts", reason);
        }
        return new Unavailable(reason, cause);
    }

    @SuppressWarnings("unchecked")
    private static Class<List<Long>> listOfLongs() {
        return (Class<List<Long>>) (Class<?>) List.class;
    }

    static String redisKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return PREFIX + HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
