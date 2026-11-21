package com.ebike.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same Caffeine caches Boot would build from spring.cache.*, but wrapped so that an eviction made inside a transaction
 * only runs after that transaction commits. Otherwise a page read between the eviction and the commit would put the
 * old stock back into the cache, and customers told "back in stock" would still see a sold-out bike.
 */
@Configuration(proxyBeanMethods = false)
public class CacheConfiguration {

    @Bean
    public CacheManager cacheManager(@Value("${spring.cache.caffeine.spec:maximumSize=500,expireAfterWrite=10m}") String spec) {
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setCacheSpecification(spec);
        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}
