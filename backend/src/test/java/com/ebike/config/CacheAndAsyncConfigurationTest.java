package com.ebike.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** V22 changed two things for the whole application: when caches are cleared, and which executors exist. */
class CacheAndAsyncConfigurationTest {

    @Test
    void cacheChangesInsideATransactionWaitForTheCommit() {
        CacheManager manager = new CacheConfiguration().cacheManager("maximumSize=10");
        assertTrue(manager instanceof TransactionAwareCacheManagerProxy);
        Cache cache = manager.getCache("products");
        assertNotNull(cache);
        cache.put("kinetic-x", "sold out");

        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.clear();
            assertEquals("sold out", cache.get("kinetic-x").get(), "still cached before the commit");
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertNull(cache.get("kinetic-x"), "cleared right after the commit");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cacheChangesOutsideATransactionHappenAtOnce() {
        Cache cache = new CacheConfiguration().cacheManager("maximumSize=10").getCache("products");
        cache.put("kinetic-x", "sold out");
        cache.clear();
        assertNull(cache.get("kinetic-x"));
    }

    @Test
    void theDefaultTaskExecutorIsDeclaredUnderBothNamesSpringLooksFor() throws Exception {
        Method method = AsyncConfiguration.class.getMethod("applicationTaskExecutor", TaskExecutorBuilder.class);
        String[] names = method.getAnnotation(Bean.class).name();
        assertTrue(Arrays.asList(names).contains("applicationTaskExecutor"));
        assertTrue(Arrays.asList(names).contains("taskExecutor"));
        assertNotNull(new AsyncConfiguration().applicationTaskExecutor(new TaskExecutorBuilder()));
    }

    @Test
    void aFullNotificationQueueSlowsTheSenderInsteadOfLosingTheNotice() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfiguration().notificationExecutor();
        executor.initialize();
        try {
            assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy);
        } finally {
            executor.shutdown();
        }
    }
}
