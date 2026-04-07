package com.brixo.slidehub.state.model;

/**
 * Estado del modo demo — puede ser "slides" (con slide activo) o "url" (con
 * iframe URL).
 * El campo returnSlide se usa en Fase 4 para volver al slide previo tras un
 * demo.
 */
public record DemoState(String mode, Integer slide, String url, Integer returnSlide) {

    /** Estado inicial por defecto: modo slides, slide 1. */
    public static DemoState defaultSlides() {
        return new DemoState("slides", 1, null, null);
    }
}
