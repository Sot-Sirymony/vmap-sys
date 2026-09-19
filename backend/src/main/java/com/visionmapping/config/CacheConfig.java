package com.visionmapping.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.response.AppearancePreferencesResponse;
import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.dto.response.GoalSynergyLinkResponse;
import com.visionmapping.dto.response.GratitudeEntryResponse;
import com.visionmapping.dto.response.IdealPartnerProfileResponse;
import com.visionmapping.dto.response.IssueReportResponse;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.dto.response.PartnerResponse;
import com.visionmapping.dto.response.ProgressLogResponse;
import com.visionmapping.dto.response.ReviewResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.dto.response.WorkStyleProfileResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * FR-30 cache layer. The backend (`spring.cache.type`) is chosen per profile:
 * `redis` when a CACHE_TYPE env var enables it, `simple` for tests, and `none`
 * by default so the application behaves exactly as before when no Redis is
 * configured. Values are the response DTOs serialized as plain JSON (BR-23) —
 * each cache names its value type, so no class metadata is stored.
 *
 * Every read cache follows the "list" / single-entity split below because a
 * cache name is bound to exactly one serialized shape: `fooList` holds
 * {@code List<FooResponse>}, `foo` holds a single {@code FooResponse}.
 * Partner and communication-message searches are paginated and highly
 * parameterized (free-text search), so only their single-entity `get` is
 * cached — caching every filter/page combination would have a poor hit rate
 * for little benefit.
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig implements CachingConfigurer {

    public static final String DASHBOARD_CACHE = "dashboard";

    public static final String VISION_AREA_CACHE = "visionArea";
    public static final String VISION_AREA_LIST_CACHE = "visionAreaList";
    public static final String DREAM_CACHE = "dream";
    public static final String DREAM_LIST_CACHE = "dreamList";
    public static final String GOAL_CACHE = "goal";
    public static final String GOAL_LIST_CACHE = "goalList";
    public static final String STEP_CACHE = "step";
    public static final String STEP_LIST_CACHE = "stepList";
    public static final String TASK_CACHE = "task";
    public static final String TASK_LIST_CACHE = "taskList";
    public static final String PARTNER_CACHE = "partner";
    public static final String REVIEW_CACHE = "review";
    public static final String REVIEW_LIST_CACHE = "reviewList";
    public static final String OBSTACLE_CACHE = "obstacle";
    public static final String OBSTACLE_LIST_CACHE = "obstacleList";
    public static final String PROGRESS_LOG_CACHE = "progressLog";
    public static final String PROGRESS_LOG_LIST_CACHE = "progressLogList";
    public static final String COMMUNICATION_MESSAGE_CACHE = "communicationMessage";
    public static final String IDEAL_PARTNER_PROFILE_CACHE = "idealPartnerProfile";
    public static final String IDEAL_PARTNER_PROFILE_LIST_CACHE = "idealPartnerProfileList";
    public static final String GRATITUDE_ENTRY_CACHE = "gratitudeEntry";
    public static final String GRATITUDE_ENTRY_LIST_CACHE = "gratitudeEntryList";
    // Single-row-per-user reads, fetched on nearly every page load — worth
    // caching even though there's no "list" counterpart.
    public static final String WORK_STYLE_PROFILE_CACHE = "workStyleProfile";
    public static final String APPEARANCE_PREFERENCE_CACHE = "appearancePreference";
    // Small, bounded, per-parent reads — same low-risk shape as the caches
    // above, just added later.
    public static final String GOAL_SYNERGY_LINK_LIST_CACHE = "goalSynergyLinkList";
    public static final String ISSUE_REPORT_CACHE = "issueReport";
    public static final String ISSUE_REPORT_LIST_CACHE = "issueReportList";

    /** Backstop for missed evictions; explicit eviction is the primary freshness mechanism. */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "vms::";

    private final UserScopedKeyGenerator userScopedKeyGenerator;

    /** Every @Cacheable in the application gets user-scoped keys (BR-20). */
    @Override
    public KeyGenerator keyGenerator() {
        return userScopedKeyGenerator;
    }

    /** Cache failures degrade to a miss, never to a failed request (BR-21). */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(ObjectMapper objectMapper) {
        Map<String, RedisCacheConfiguration> configs = Map.ofEntries(
                Map.entry(DASHBOARD_CACHE, dtoCache(objectMapper, DashboardSummaryResponse.class)),
                Map.entry(VISION_AREA_CACHE, dtoCache(objectMapper, VisionAreaResponse.class)),
                Map.entry(VISION_AREA_LIST_CACHE, listCache(objectMapper, VisionAreaResponse.class)),
                Map.entry(DREAM_CACHE, dtoCache(objectMapper, DreamResponse.class)),
                Map.entry(DREAM_LIST_CACHE, listCache(objectMapper, DreamResponse.class)),
                Map.entry(GOAL_CACHE, dtoCache(objectMapper, GoalResponse.class)),
                Map.entry(GOAL_LIST_CACHE, listCache(objectMapper, GoalResponse.class)),
                Map.entry(STEP_CACHE, dtoCache(objectMapper, VisionStepResponse.class)),
                Map.entry(STEP_LIST_CACHE, listCache(objectMapper, VisionStepResponse.class)),
                Map.entry(TASK_CACHE, dtoCache(objectMapper, TaskItemResponse.class)),
                Map.entry(TASK_LIST_CACHE, listCache(objectMapper, TaskItemResponse.class)),
                Map.entry(PARTNER_CACHE, dtoCache(objectMapper, PartnerResponse.class)),
                Map.entry(REVIEW_CACHE, dtoCache(objectMapper, ReviewResponse.class)),
                Map.entry(REVIEW_LIST_CACHE, listCache(objectMapper, ReviewResponse.class)),
                Map.entry(OBSTACLE_CACHE, dtoCache(objectMapper, ObstacleResponse.class)),
                Map.entry(OBSTACLE_LIST_CACHE, listCache(objectMapper, ObstacleResponse.class)),
                Map.entry(PROGRESS_LOG_CACHE, dtoCache(objectMapper, ProgressLogResponse.class)),
                Map.entry(PROGRESS_LOG_LIST_CACHE, listCache(objectMapper, ProgressLogResponse.class)),
                Map.entry(COMMUNICATION_MESSAGE_CACHE, dtoCache(objectMapper, CommunicationMessageResponse.class)),
                Map.entry(IDEAL_PARTNER_PROFILE_CACHE, dtoCache(objectMapper, IdealPartnerProfileResponse.class)),
                Map.entry(IDEAL_PARTNER_PROFILE_LIST_CACHE, listCache(objectMapper, IdealPartnerProfileResponse.class)),
                Map.entry(GRATITUDE_ENTRY_CACHE, dtoCache(objectMapper, GratitudeEntryResponse.class)),
                Map.entry(GRATITUDE_ENTRY_LIST_CACHE, listCache(objectMapper, GratitudeEntryResponse.class)),
                Map.entry(WORK_STYLE_PROFILE_CACHE, dtoCache(objectMapper, WorkStyleProfileResponse.class)),
                Map.entry(APPEARANCE_PREFERENCE_CACHE, dtoCache(objectMapper, AppearancePreferencesResponse.class)),
                Map.entry(GOAL_SYNERGY_LINK_LIST_CACHE, listCache(objectMapper, GoalSynergyLinkResponse.class)),
                Map.entry(ISSUE_REPORT_CACHE, dtoCache(objectMapper, IssueReportResponse.class)),
                Map.entry(ISSUE_REPORT_LIST_CACHE, listCache(objectMapper, IssueReportResponse.class)));
        return builder -> builder.withInitialCacheConfigurations(configs);
    }

    private static RedisCacheConfiguration dtoCache(ObjectMapper objectMapper, Class<?> valueType) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(CACHE_TTL)
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(objectMapper, valueType)));
    }

    private static RedisCacheConfiguration listCache(ObjectMapper objectMapper, Class<?> elementType) {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(CACHE_TTL)
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(objectMapper, listType)));
    }
}
