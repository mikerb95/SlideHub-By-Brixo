package com.brixo.slidehub.state.controller;

import com.brixo.slidehub.state.model.Device;
import com.brixo.slidehub.state.service.DeviceRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API del registro de dispositivos (HU-014, HU-015).
 * Acceso protegido — en producción sólo ADMIN puede llamar estos endpoints.
 * La seguridad en Phase 0 se delega al gateway o al ui-service.
 *
 * GET /api/devices → lista todos los dispositivos
 * GET /api/devices/token/{token} → busca dispositivo por token único
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
}
