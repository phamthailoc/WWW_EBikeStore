package com.ebike.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
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
    public static final String DOCUMENT_EXECUTOR = "documentExecutor";
    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";
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
        // a normal shutdown finishes the emails already queued (up to 30 seconds); a crash still loses them
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * V27: the hand-over emails (they carry PDFs that take a moment to make) have a pool of their own, so a big restock or reminder run on the mail
     * pool cannot push them out. A full queue is not hidden: the task is rejected and the listener logs the order id.
     */
    @Bean(DOCUMENT_EXECUTOR)
    public ThreadPoolTaskExecutor documentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("documents-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * Spring Boot creates its default "applicationTaskExecutor" (used by MVC async requests and by spring.task.execution.*
     * settings) only when the application has no Executor bean at all. The pools above are Executor beans, so the
     * default is declared here again, built by Boot's own builder so that spring.task.execution.* still applies.
     */
    @Bean(name = {TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME, AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME})
    public ThreadPoolTaskExecutor applicationTaskExecutor(TaskExecutorBuilder builder) {
        return builder.build();
    }

    /**
     * In-app notifications of a large restock. The queue is bounded but a full queue is never dropped: the thread that
     * published the event runs the task itself, which slows the sender down instead of losing the notification.
     */
    @Bean(NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notify-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
