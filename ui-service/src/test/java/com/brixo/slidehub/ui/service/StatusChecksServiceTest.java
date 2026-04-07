package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.StatusCheckItem;
import com.brixo.slidehub.ui.model.StatusChecksResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusChecksServiceTest {

    @Test
    void getChecks_withCache_returnsSameSnapshotWithinTtl() {
        AtomicInteger calls = new AtomicInteger();

        StatusChecksService service = new StatusChecksService(
                "http://localhost:8081",
                "http://localhost:8083",
                "http://localhost:8080",
                "",
                "",
                6379,
                "",
                "",
                "",
                "us-east-1",
                100,
                5000) {
            @Override
            StatusCheckItem checkHttp(String name, String url, Instant timestamp) {
                calls.incrementAndGet();
                return new StatusCheckItem(name, "ok", 10L, timestamp, "HTTP 200");
            }

            @Override
            StatusCheckItem checkTcp(String name, String host, int port, Instant timestamp) {
                calls.incrementAndGet();
                return new StatusCheckItem(name, "ok", 10L, timestamp, "TCP connected");
            }
        };

        StatusChecksResponse first = service.getChecks();
        StatusChecksResponse second = service.getChecks();

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.generatedAt(), second.generatedAt());
        assertEquals(3, calls.get());
    }

    @Test
    void buildChecks_containsExpectedTargetNames() {
        StatusChecksService service = new StatusChecksService(
                "http://localhost:8081",
                "http://localhost:8083",
                "http://localhost:8080",
                "",
                "",
                6379,
                "",
                "",
                "",
                "us-east-1",
                100,
                1);

        StatusChecksResponse response = service.buildChecks(Instant.parse("2026-04-01T10:00:00Z"));

        assertEquals(8, response.checks().size());
        assertTrue(response.checks().stream().anyMatch(c -> c.name().equals("state-service")));
        assertTrue(response.checks().stream().anyMatch(c -> c.name().equals("ai-service")));
        assertTrue(response.checks().stream().anyMatch(c -> c.name().equals("gateway")));
        assertTrue(response.checks().stream().anyMatch(c -> c.name().equals("render")));
        assertTrue(response.checks().stream().anyMatch(c -> c.name().equals("redis")));
        assertTrue(response.checks().stream().anyMatch(c -> c.name().equals("mongodb")));
        assertTrue(response.checks().stream().anyMatch(c -> c.name().equals("postgres")));
        assertTrue(response.checks().stream().anyMatch(c -> c.name().equals("aws-s3")));
    }
}
