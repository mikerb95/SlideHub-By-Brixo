package com.brixo.slidehub.state.model;

/** Request body para POST /api/slide */
public record SetSlideRequest(int slide, Integer totalSlides) {
}
