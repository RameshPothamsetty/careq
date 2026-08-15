package com.careq.doctor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis caching for the doctor/department catalog.
 *
 * <p><b>Scope boundary:</b> only the read-heavy, rarely-changing catalog
 * endpoints are cached ({@code GET /api/doctors} and {@code GET /api/departments})
 * with a short 60s TTL. Live queue data — position, predicted wait and
 * availability — is deliberately NOT cached; availability is always read
 * fresh from the database so a doctor going offline blocks joins immediately.
 *
 * <p><b>Resilience:</b> a custom {@link CacheErrorHandler} logs and swallows
 * every Redis failure. If Redis is briefly unreachable, reads fall through to
 * the database and evictions are skipped — the catalog never 500s because a
 * cache layer is down (the same "graceful degradation" rule as the AI triage
 * fallback and the RabbitMQ publishing path).
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Catalog entries change rarely — 60s keeps reads fresh without DB churn. */
    private static final Duration CATALOG_TTL = Duration.ofSeconds(60);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(CATALOG_TTL)
                .disableCachingNullValues()
                // careq:doctorCatalog::<key> — scoped, human-inspectable keys (redis-cli KEYS careq:*)
                .computePrefixWith(cacheName -> "careq:" + cacheName + "::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JdkSerializationRedisSerializer()));
        return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache GET failed for cache '{}' key {} — falling through to the database: {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed for cache '{}' key {} — skipping cache write: {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for cache '{}' key {}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Redis cache CLEAR failed for cache '{}': {}", cache.getName(), e.getMessage());
            }
        };
    }
}
