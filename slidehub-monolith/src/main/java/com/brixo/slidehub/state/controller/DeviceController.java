package com.brixo.slidehub.state.controller;

import com.brixo.slidehub.state.model.Device;
import com.brixo.slidehub.state.model.RegisterDeviceRequest;
import com.brixo.slidehub.state.service.DeviceRegistryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API del registro de dispositivos (HU-014, HU-015).
 * Acceso protegido — en producción sólo ADMIN puede llamar estos endpoints.
 * La seguridad en Phase 0 se delega al gateway o al ui-service.
 *
 * GET /api/devices → lista todos los dispositivos
 * GET /api/devices/token/{token} → busca dispositivo por token único
 * POST /api/devices/register → alta o actualización (upsert)
 * POST /api/devices/heartbeat → ping de presencia (upsert)
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceRegistryService deviceRegistryService;

    public DeviceController(DeviceRegistryService deviceRegistryService) {
        this.deviceRegistryService = deviceRegistryService;
    }

    /** Lista todos los dispositivos registrados (HU-014). */
    @GetMapping
    public ResponseEntity<List<Device>> getAllDevices() {
        return ResponseEntity.ok(deviceRegistryService.findAll());
    }

    /** Busca un dispositivo por su token único (HU-015). */
    @GetMapping("/token/{token}")
    public ResponseEntity<Device> getDeviceByToken(@PathVariable String token) {
        return deviceRegistryService.findByToken(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Alta o actualización de un dispositivo (upsert). */
    @PostMapping("/register")
    public ResponseEntity<Device> registerDevice(@RequestBody RegisterDeviceRequest request,
            HttpServletRequest httpRequest) {
        return upsert(request, httpRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    /** Heartbeat de presencia: reutiliza el mismo upsert de registro. */
    @PostMapping("/heartbeat")
    public ResponseEntity<Device> heartbeat(@RequestBody RegisterDeviceRequest request,
            HttpServletRequest httpRequest) {
        return upsert(request, httpRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    private java.util.Optional<Device> upsert(RegisterDeviceRequest request, HttpServletRequest httpRequest) {
        String name = normalizeOrDefault(request.name(), "unknown-device");
        String type = normalizeOrDefault(request.type(), "unknown");
        String token = normalizeOrDefault(request.token(), null);

        if (token == null) {
            return java.util.Optional.empty();
        }

        Device device = new Device(
                name,
                type,
                token,
                resolveClientIp(httpRequest),
                LocalDateTime.now());

        return java.util.Optional.of(deviceRegistryService.register(device));
    }

    private String normalizeOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
