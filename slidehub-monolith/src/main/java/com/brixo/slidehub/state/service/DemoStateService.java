package com.brixo.slidehub.state.service;

import com.brixo.slidehub.state.model.DemoState;
import com.brixo.slidehub.state.model.SetDemoRequest;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Gestiona el estado del modo demo en Redis.
 * La clave Redis "demo_state" guarda: { "mode": "slides|url", "slide": N,
 * "url": "...", "returnSlide": N }
 *
 * Fase 4: al volver a modo "slides", restaura automáticamente el slide guardado
 * en returnSlide (PLAN-EXPANSION.md Fase 4, tarea 41).
 */
@Service
public class DemoStateService {

    private static final Logger log = LoggerFactory.getLogger(DemoStateService.class);
    private static final String DEMO_KEY = "demo_state";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SlideStateService slideStateService;

    public DemoStateService(StringRedisTemplate redis, ObjectMapper objectMapper,
            SlideStateService slideStateService) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.slideStateService = slideStateService;
    }

    /**
     * Retorna el estado demo actual. Si no hay estado previo, retorna modo "slides"
     * con slide 1.
     */
    public DemoState getDemoState() {
        String raw = redis.opsForValue().get(DEMO_KEY);
        if (raw == null) {
            return DemoState.defaultSlides();
        }
        try {
            return objectMapper.readValue(raw, DemoState.class);
        } catch (Exception e) {
            log.warn("Error parseando demo state desde Redis — usando default: {}", e.getMessage());
            return DemoState.defaultSlides();
        }
    }

    /**
     * Actualiza el estado demo. Si vuelve a modo "slides" y hay un returnSlide
     * guardado, restaura automáticamente ese slide en state-service (HU-010, Fase
     * 4 tarea 41).
     */
    public DemoState setDemoState(SetDemoRequest request) {
        DemoState newState;
        if ("url".equals(request.mode())) {
            newState = new DemoState("url", null, request.url(), request.returnSlide());
        } else {
            // Modo "slides": limpiar url y restaurar slide previo si existe
            DemoState current = getDemoState();
            Integer restoreSlide = request.slide();
            if (restoreSlide == null && current.returnSlide() != null) {
                restoreSlide = current.returnSlide();
            }
            if (restoreSlide == null) {
                restoreSlide = 1;
            }
            // Restaurar el slide activo en state-service (Fase 4 tarea 41)
            slideStateService.setSlide(restoreSlide, null);
            newState = new DemoState("slides", restoreSlide, null, null);
        }
        try {
            String json = objectMapper.writeValueAsString(newState);
            redis.opsForValue().set(DEMO_KEY, json);
        } catch (Exception e) {
            log.error("Error guardando demo state en Redis: {}", e.getMessage());
        }
        return newState;
    }
}
