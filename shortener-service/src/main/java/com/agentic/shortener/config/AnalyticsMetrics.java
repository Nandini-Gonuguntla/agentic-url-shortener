package com.agentic.shortener.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Makes dropped analytics events visible at /actuator/metrics/shortener.clicks.dropped. */
@Component
public class AnalyticsMetrics {

    public AnalyticsMetrics(MeterRegistry registry, AtomicLong droppedClickEvents) {
        Gauge.builder("shortener.clicks.dropped", droppedClickEvents, AtomicLong::get)
                .description("Click events dropped because the recording queue was full")
                .register(registry);
    }
}
