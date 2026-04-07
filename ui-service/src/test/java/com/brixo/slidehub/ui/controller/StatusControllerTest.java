package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.model.StatusCheckItem;
import com.brixo.slidehub.ui.model.StatusChecksResponse;
import com.brixo.slidehub.ui.service.StatusChecksService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ConcurrentModel;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class StatusControllerTest {

    @Test
    void getStatusChecks_returnsExpectedJsonPayload() {
        StatusChecksService statusChecksService = mock(StatusChecksService.class);
        StatusController controller = new StatusController(statusChecksService);

        Instant now = Instant.parse("2026-04-01T10:00:00Z");
        StatusChecksResponse response = new StatusChecksResponse(
                now,
                List.of(new StatusCheckItem("state-service", "ok", 34L, now, "HTTP 200")));

        given(statusChecksService.getChecks()).willReturn(response);

        ResponseEntity<StatusChecksResponse> result = controller.getStatusChecks();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("state-service", result.getBody().checks().get(0).name());
        assertEquals("ok", result.getBody().checks().get(0).status());
        assertEquals(34L, result.getBody().checks().get(0).latencyMs());
    }

    @Test
    void statusView_returnsTemplateName() {
        StatusChecksService statusChecksService = mock(StatusChecksService.class);
        StatusController controller = new StatusController(statusChecksService);

        String view = controller.statusView(new ConcurrentModel());

        assertEquals("status", view);
    }
}
