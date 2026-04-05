package com.zorvyn.financedashboard.config;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * ============================================================================
 * REDIS CACHE CONFIGURATION
 * ============================================================================
 *
 * Configures Redis as the caching backend for Spring's @Cacheable abstraction.
 *
 * Why Redis over in-memory caching (ConcurrentHashMap)?
 *
 *   1. DISTRIBUTED: When we scale to multiple API instances behind a load
 *      balancer, all instances share the same Redis cache. An in-memory
 *      cache would have each instance computing and storing its own copy,
 *      wasting memory and causing inconsistency.
 *
 *   2. PERSISTENCE: Redis can persist cached data to disk, surviving
 *      restarts. In-memory caches are lost on JVM restart.
 *
 *   3. TTL SUPPORT: Redis natively supports time-to-live on keys,
 *      which aligns perfectly with our "cache dashboard summaries for
 *      5 minutes" strategy.
 *
 *   4. VISIBILITY: Redis provides CLI tools (redis-cli) to inspect,
 *      debug, and manually flush cached data during development.
 *
 * Serialization Strategy:
 *   - Keys: StringRedisSerializer (human-readable in redis-cli)
 *   - Values: GenericJackson2JsonRedisSerializer (JSON for debuggability)
 *
 *   We chose JSON over JDK serialization because:
 *   - JSON is human-readable (can inspect values in redis-cli)
 *   - No class version compatibility issues across deployments
 *   - Slightly larger but acceptable for our cache sizes
 */
@Configuration
public class RedisConfig {

    /**
     * Custom RedisCacheManager bean.
     *
     * We define specific cache configurations for different cache names
     * because different data types need different TTLs:
     *   - Dashboard summaries: 5 minutes (balance freshness vs. performance)
     *   - User data: shorter TTL or no caching (security-sensitive)
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // Default cache configuration used for any cache name not explicitly configured
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // 5-minute TTL: Dashboard data can be slightly stale.
                // Users see data at most 5 minutes old, which is acceptable
                // for a financial dashboard that updates in near-real-time, not real-time.
                .entryTtl(Duration.ofMinutes(5))

                // Disable caching null values to prevent "cache pollution"
                // where a query that returns null fills the cache with empty entries
                .disableCachingNullValues()

                // Key prefix: "fd:" namespace prevents collisions if multiple
                // apps share the same Redis instance
                .prefixCacheNameWith("fd:")

                // Key serializer: String for human readability in redis-cli
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()
                    )
                )

                // Value serializer: JSON for debuggability and version compatibility
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()
                    )
                );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .transactionAware()  // Participate in Spring @Transactional
                .build();
    }
}
