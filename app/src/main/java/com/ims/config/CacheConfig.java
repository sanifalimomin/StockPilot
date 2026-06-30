package com.ims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the Redis cache non-fatal: if a cache get/put/evict fails (e.g. Redis is
 * briefly unreachable), log it and fall through to the underlying method instead
 * of failing the request. The @Cacheable consolidated-stock endpoint therefore
 * always degrades to a direct database read rather than erroring.
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache GET failed ({}/{}); falling back to source: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed ({}/{}): {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache EVICT failed ({}/{}): {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache CLEAR failed ({}): {}", cache.getName(), ex.getMessage());
            }
        };
    }
}
