package com.brixo.slidehub.state.model;

/** Request body para alta y heartbeat de dispositivos. */
public record RegisterDeviceRequest(
        String name,
        String type,
        String token) {
}
