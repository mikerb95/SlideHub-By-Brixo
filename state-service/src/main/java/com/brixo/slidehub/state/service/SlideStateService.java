package com.brixo.slidehub.state.service;

import com.brixo.slidehub.state.model.SlideStateResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Gestiona el estado del slide actual en Redis.
 * La clave Redis "current_slide" guarda: { "slide": N }
 * El campo totalSlides se calcula dinámicamente desde el directorio de slides.
 */
@Service
public class SlideStateService {

    private static final Logger log = LoggerFactory.getLogger(SlideStateService.class);
    private static final String SLIDE_KEY = "current_slide";
    private static final String SLIDE_FIELD = "slide";
    private static final String TOTAL_SLIDES_FIELD = "totalSlides";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${slidehub.slides.directory:./slides}")
    private String slidesDirectory;

    public SlideStateService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Devuelve el slide actual y el total de slides (HU-008).
     * Si no hay estado previo, retorna slide=1.
     */
    public SlideStateResponse getCurrentSlide() {
        JsonNode state = readStoredState();
        int slide = 1;
        int totalSlides = countSlides();

        if (state != null) {
            slide = state.path(SLIDE_FIELD).asInt(1);
            int storedTotalSlides = state.path(TOTAL_SLIDES_FIELD).asInt(0);
            if (storedTotalSlides > 0) {
                totalSlides = storedTotalSlides;
            }
        }

        return new SlideStateResponse(slide, totalSlides);
    }

    /**
     * Establece el slide actual. Respeta los límites [1, totalSlides] (HU-004
     * §3,§4).
     * Si totalSlides = 0, guarda el valor tal cual (sin slides importados aún).
     */
    public SlideStateResponse setSlide(int requestedSlide, Integer requestedTotalSlides) {
        int total = resolveTotalSlides(requestedTotalSlides);
        int bounded = total > 0
                ? Math.max(1, Math.min(requestedSlide, total))
                : Math.max(1, requestedSlide);

        storeSlideState(bounded, total);
        return new SlideStateResponse(bounded, total);
    }

    /**
     * Cuenta los archivos de imagen en el directorio de slides configurado.
     * Retorna 0 si el directorio no existe o no se puede leer.
     */
    private int countSlides() {
        for (Path candidate : resolveSlideDirectories()) {
            int count = countSlides(candidate);
            if (count > 0) {
                return count;
            }
        }

        return 0;
    }

    private int countSlides(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }

        try (var stream = Files.list(dir)) {
            return (int) stream
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                    })
                    .count();
        } catch (IOException e) {
            log.warn("Error contando slides en {}: {}", dir, e.getMessage());
            return 0;
        }
    }

    private List<Path> resolveSlideDirectories() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        addCandidate(candidates, slidesDirectory);
        addCandidate(candidates, "./slides");
        addCandidate(candidates, "./static/slides");
        addCandidate(candidates, "./ui-service/src/main/resources/static/slides");
        addCandidate(candidates, "../ui-service/src/main/resources/static/slides");
        return new ArrayList<>(candidates);
    }

    private void addCandidate(LinkedHashSet<Path> candidates, String location) {
        if (location == null || location.isBlank()) {
            return;
        }

        candidates.add(Path.of(location).normalize());
    }

    private JsonNode readStoredState() {
        String raw = redis.opsForValue().get(SLIDE_KEY);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("Error parseando estado de slide desde Redis: {}", e.getMessage());
            return null;
        }
    }

    private int resolveTotalSlides(Integer requestedTotalSlides) {
        if (requestedTotalSlides != null && requestedTotalSlides > 0) {
            return requestedTotalSlides;
        }

        JsonNode storedState = readStoredState();
        if (storedState != null) {
            int storedTotalSlides = storedState.path(TOTAL_SLIDES_FIELD).asInt(0);
            if (storedTotalSlides > 0) {
                return storedTotalSlides;
            }
        }

        return countSlides();
    }

    private void storeSlideState(int slide, int totalSlides) {
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put(SLIDE_FIELD, slide);
            if (totalSlides > 0) {
                state.put(TOTAL_SLIDES_FIELD, totalSlides);
            }
            redis.opsForValue().set(SLIDE_KEY, objectMapper.writeValueAsString(state));
        } catch (Exception e) {
            log.error("Error guardando estado de slide en Redis: {}", e.getMessage());
        }
    }
}
