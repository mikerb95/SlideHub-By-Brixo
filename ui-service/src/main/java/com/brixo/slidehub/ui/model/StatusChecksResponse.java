package com.brixo.slidehub.ui.model;

import java.time.Instant;
import java.util.List;

public record StatusChecksResponse(
        Instant generatedAt,
        List<StatusCheckItem> checks) {
}
