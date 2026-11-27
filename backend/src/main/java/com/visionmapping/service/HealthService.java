package com.visionmapping.service;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

/** The health answer, with the cache's state. V33 (D-33-11) moved it here from the controller. */
@Service
public class HealthService {

    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;
    private final String cacheType;

    public HealthService(ObjectProvider<RedisConnectionFactory> redisConnectionFactory,
                         @Value("${spring.cache.type:none}") String cacheType) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.cacheType = cacheType;
    }

    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "Vision Mapping Management System Backend",
                "cache", cacheDetail());
    }

    String cacheDetail() {
        if (!"redis".equalsIgnoreCase(cacheType)) {
            return cacheType;
        }
        RedisConnectionFactory factory = redisConnectionFactory.getIfAvailable();
        if (factory == null) {
            return "redis:down";
        }
        try (RedisConnection connection = factory.getConnection()) {
            connection.ping();
            return "redis:up";
        } catch (RuntimeException unreachable) {
            // Broad on purpose: whatever the driver throws, the honest health
            // answer is "down", and the health check itself must not fail.
            return "redis:down";
        }
    }
}
