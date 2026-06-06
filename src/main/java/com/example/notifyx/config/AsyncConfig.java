package com.example.notifyx.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for in-process notification processing.
 * Provides the thread pool that NotificationQueueService uses for @Async methods.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${notifyx.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${notifyx.async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${notifyx.async.queue-capacity:500}")
    private int queueCapacity;

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("notifyx-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
