package com.brixo.slidehub.state.controller;

import com.brixo.slidehub.state.model.SetSlideRequest;
import com.brixo.slidehub.state.model.SlideStateResponse;
import com.brixo.slidehub.state.service.SlideStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de navegación de slides (HU-004, HU-008).
 *
 * GET /api/slide → { "slide": N, "totalSlides": M }
 * POST /api/slide → { "slide": N, "totalSlides": M }
 */
@RestController
@RequestMapping("/api/slide")
public class SlideController {

    private final SlideStateService slideStateService;

    public SlideController(SlideStateService slideStateService) {
        this.slideStateService = slideStateService;
    }

    /**
     * Retorna el slide activo y el total de slides.
     * Si no hay estado previo, retorna slide=1 (HU-008 §2).
     */
    @GetMapping
    public ResponseEntity<SlideStateResponse> getSlide() {
        return ResponseEntity.ok(slideStateService.getCurrentSlide());
    }

    /**
     * Actualiza el slide activo. Respeta los límites [1, totalSlides] (HU-004
     * §3,§4).
     */
    @PostMapping
    public ResponseEntity<SlideStateResponse> setSlide(@RequestBody SetSlideRequest request) {
        return ResponseEntity.ok(slideStateService.setSlide(request.slide(), request.totalSlides()));
    }
}
