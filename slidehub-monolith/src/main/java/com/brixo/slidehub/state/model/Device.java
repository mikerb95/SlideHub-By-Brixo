package com.brixo.slidehub.state.model;

import java.time.LocalDateTime;

/**
 * Dispositivo registrado (HU-014, HU-015).
 * Un dispositivo es cualquier pantalla o control remoto conectado al sistema.
 */
public record Device(
        String name,
        String type,
        String token,
        String lastIp,
        LocalDateTime lastConnection) {
}
