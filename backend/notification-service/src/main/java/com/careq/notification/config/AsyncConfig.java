package com.careq.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * {@code @Async} support for fire-and-forget Web Push delivery.
 *
 * <p>Delivery runs on a small dedicated pool so a slow push service can never
 * block the RabbitMQ consumer thread (which must ack events promptly) and can
 * never delay the in-app notification persistence.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "webPushExecutor")
    public Executor webPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("webpush-");
        executor.initialize();
        return executor;
    }
}
