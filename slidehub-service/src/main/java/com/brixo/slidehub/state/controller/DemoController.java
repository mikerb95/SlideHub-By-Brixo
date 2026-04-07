package com.brixo.slidehub.state.controller;

import com.brixo.slidehub.state.model.DemoState;
import com.brixo.slidehub.state.model.SetDemoRequest;
import com.brixo.slidehub.state.service.DemoStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API del modo demo — alterna entre slides e iframe de URL (HU-010, HU-011).
 *
 * GET /api/demo → { "mode": "slides|url", "slide": N, "url": "...",
 * "returnSlide": N }
 * POST /api/demo → actualiza el modo
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoStateService demoStateService;

    public DemoController(DemoStateService demoStateService) {
        this.demoStateService = demoStateService;
    }

    /** Retorna el estado demo actual. */
    @GetMapping
    public ResponseEntity<DemoState> getDemoState() {
        return ResponseEntity.ok(demoStateService.getDemoState());
    }

    /** Actualiza el modo demo (slides ↔ url). */
    @PostMapping
    public ResponseEntity<DemoState> setDemoState(@RequestBody SetDemoRequest request) {
        return ResponseEntity.ok(demoStateService.setDemoState(request));
    }
}
