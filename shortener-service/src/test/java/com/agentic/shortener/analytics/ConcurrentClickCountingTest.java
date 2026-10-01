package com.agentic.shortener.analytics;

import com.agentic.shortener.domain.Link;
import com.agentic.shortener.domain.LinkRepository;
import com.agentic.shortener.service.LinkService;
import com.agentic.shortener.service.ResolvedLink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards against lost updates: concurrent clicks must all be counted. */
@SpringBootTest(properties = "shortener.analytics.async=false")
class ConcurrentClickCountingTest {

    @Autowired
    private LinkService linkService;

    @Autowired
    private ClickRecorder clickRecorder;

    @Autowired
    private LinkRepository links;

    @Test
    void concurrentClicksAreAllCounted() throws Exception {
        Link link = linkService.create("https://example.com/concurrent", null);
        ResolvedLink resolved = linkService.resolve(link.getCode());
        int clicks = 50;

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < clicks; i++) {
            String address = "192.168.0." + i;
            tasks.add(() -> {
                clickRecorder.record(resolved, address, null);
                return null;
            });
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (var future : pool.invokeAll(tasks)) {
                future.get();
            }
        }

        assertThat(links.findByCode(link.getCode()).orElseThrow().getClickCount()).isEqualTo(clicks);
    }
}
