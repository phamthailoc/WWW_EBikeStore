package com.ebike.config;

import com.ebike.productModule.dto.response.ProductDetailDto;
import com.ebike.productModule.dto.response.ProductSummaryDto;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisCacheConfiguration implements CachingConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCacheConfiguration.class);

    public static final String PRODUCT_LIST_CACHE = "products:v2:list";
    public static final String PRODUCT_DETAIL_CACHE = "products:v2:detail";

    @Bean
    public org.springframework.data.redis.cache.RedisCacheConfiguration cacheConfiguration() {
        return org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .disableCachingNullValues()
            .computePrefixWith(CacheKeyPrefix.simple())
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer productCacheCustomizer(ObjectMapper objectMapper) {
        JavaType productListType = objectMapper.getTypeFactory()
            .constructCollectionType(List.class, ProductSummaryDto.class);

        return builder -> builder
            .withCacheConfiguration(
                PRODUCT_LIST_CACHE,
                cacheConfiguration()
                    .entryTtl(Duration.ofMinutes(10))
                    .serializeValuesWith(jsonSerializer(objectMapper, productListType))
            )
            .withCacheConfiguration(
                PRODUCT_DETAIL_CACHE,
                cacheConfiguration()
                    .entryTtl(Duration.ofMinutes(30))
                    .serializeValuesWith(jsonSerializer(objectMapper, objectMapper.constructType(ProductDetailDto.class)))
            );
    }

    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> method.getName() + "::" + org.springframework.cache.interceptor.SimpleKeyGenerator.generateKey(params);
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                logCacheFailure("GET", exception, cache, key);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                logCacheFailure("PUT", exception, cache, key);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                logCacheFailure("EVICT", exception, cache, key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                logCacheFailure("CLEAR", exception, cache, "all");
            }
        };
    }

    private RedisSerializationContext.SerializationPair<Object> jsonSerializer(ObjectMapper objectMapper, JavaType javaType) {
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, javaType);
        return RedisSerializationContext.SerializationPair.fromSerializer(serializer);
    }

    private void logCacheFailure(String operation, RuntimeException exception, Cache cache, Object key) {
        LOGGER.warn(
            "Redis cache {} failed for cache '{}' and key '{}'; falling back to primary data source.",
            operation,
            cache == null ? "unknown" : cache.getName(),
            key,
            exception
        );
    }
}
