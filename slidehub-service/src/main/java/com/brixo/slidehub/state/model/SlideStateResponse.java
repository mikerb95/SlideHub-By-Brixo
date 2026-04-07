package com.brixo.slidehub.state.model;

/**
 * Respuesta del endpoint GET /api/slide (HU-008).
 * El campo totalSlides refleja el conteo real del directorio de slides.
 */
public record SlideStateResponse(int slide, int totalSlides) {
}
