package com.ebike.config;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Background sending of emails (V22 back-in-stock). The pool and its queue are bounded: when a very large restock fills
 * the queue, extra emails are dropped with a warning instead of piling up memory or blocking the manager's request.
 * The in-app notification is stored separately, so nobody misses the news itself.
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    public static final String MAIL_EXECUTOR = "mailExecutor";
    private static final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);

    @Bean(MAIL_EXECUTOR)
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler((task, pool) ->
            log.warn("Mail queue is full ({} waiting); one email was dropped", pool.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
