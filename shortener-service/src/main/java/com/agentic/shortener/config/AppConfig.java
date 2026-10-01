package com.agentic.shortener.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Counts analytics events dropped under overload; published as a gauge by {@link AnalyticsMetrics}. */
    @Bean
    public AtomicLong droppedClickEvents() {
        return new AtomicLong();
    }

    /**
     * Bounded executor for click recording. When the queue is full, events are dropped and
     * counted rather than slowing down redirects: availability of the redirect path wins
     * over completeness of analytics. With {@code async=false} recording runs inline (tests).
     */
    @Bean
    public Executor clickRecordingExecutor(ShortenerProperties properties, AtomicLong droppedClickEvents) {
        if (!properties.analytics().async()) {
            return Runnable::run;
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("click-recorder-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(properties.analytics().queueCapacity());
        executor.setRejectedExecutionHandler((task, pool) -> {
            long dropped = droppedClickEvents.incrementAndGet();
            if (dropped % 1000 == 1) {
                log.warn("Click recording queue full; {} events dropped so far", dropped);
            }
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
