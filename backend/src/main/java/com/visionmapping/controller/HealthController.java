package com.visionmapping.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;

    @Value("${spring.cache.type:none}")
    private String cacheType;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Vision Mapping Management System Backend",
                "cache", cacheDetail()
        ));
    }

    /**
     * The cache is an accelerator, not a dependency (BR-21): a down Redis is
     * reported here as detail but never turns the service status itself to
     * DOWN — Render's health check must keep passing.
     */
    private String cacheDetail() {
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
