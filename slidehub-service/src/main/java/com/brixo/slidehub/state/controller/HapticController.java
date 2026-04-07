package com.brixo.slidehub.state.controller;

import com.brixo.slidehub.state.model.PublishHapticRequest;
import com.brixo.slidehub.state.service.HapticEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/haptics/events")
public class HapticController {

    private final HapticEventService hapticEventService;

    public HapticController(HapticEventService hapticEventService) {
        this.hapticEventService = hapticEventService;
    }

    @PostMapping("/publish")
    public ResponseEntity<?> publish(@RequestBody PublishHapticRequest request) {
        try {
            hapticEventService.publish(request.participantToken(), request.pattern(), request.message());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/next")
    public ResponseEntity<?> next(@RequestParam("participantToken") String participantToken) {
        return hapticEventService.popNext(participantToken)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
