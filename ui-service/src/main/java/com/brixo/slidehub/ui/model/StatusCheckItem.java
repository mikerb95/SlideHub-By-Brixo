package com.brixo.slidehub.ui.model;

import java.time.Instant;

public record StatusCheckItem(
        String name,
        String status,
        Long latencyMs,
        Instant lastCheckedAt,
        String detail) {
}
