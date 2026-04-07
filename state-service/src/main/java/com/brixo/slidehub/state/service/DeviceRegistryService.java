package com.brixo.slidehub.state.service;

import com.brixo.slidehub.state.model.Device;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de dispositivos conectados (HU-014, HU-015).
 *
 * TODO: En Fase 2 o posterior, persistir en Redis con HSET o en PostgreSQL.
 * Por ahora se usa un mapa en memoria — se pierde al reiniciar el servicio.
 */
@Service
public class DeviceRegistryService {

    private final Map<String, Device> devices = new ConcurrentHashMap<>();

    /** Lista todos los dispositivos registrados. */
    public List<Device> findAll() {
        return List.copyOf(devices.values());
    }

    /** Busca un dispositivo por su token único. */
    public Optional<Device> findByToken(String token) {
        return Optional.ofNullable(devices.get(token));
    }

    /** Registra o actualiza un dispositivo. */
    public Device register(Device device) {
        devices.put(device.token(), device);
        return device;
    }
}
