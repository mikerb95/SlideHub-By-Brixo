package com.brixo.slidehub.state.model;

/** Request body para POST /api/demo */
public record SetDemoRequest(String mode, Integer slide, String url, Integer returnSlide) {
}
