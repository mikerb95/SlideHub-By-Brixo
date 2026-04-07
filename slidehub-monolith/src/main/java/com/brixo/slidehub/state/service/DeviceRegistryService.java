package com.brixo.slidehub.state.service;

import com.brixo.slidehub.state.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registro de dispositivos conectados (HU-014, HU-015).
 */
@Service
public class DeviceRegistryService {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistryService.class);
    private static final String DEVICES_KEY = "devices_registry";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DeviceRegistryService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Lista todos los dispositivos registrados. */
    public List<Device> findAll() {
        List<Object> rawDevices = redis.opsForHash().values(DEVICES_KEY);
        List<Device> devices = new ArrayList<>();

        for (Object rawDevice : rawDevices) {
            parseDevice(rawDevice).ifPresent(devices::add);
        }

        return devices;
    }

    /** Busca un dispositivo por su token único. */
    public Optional<Device> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Object rawDevice = redis.opsForHash().get(DEVICES_KEY, token);
        return parseDevice(rawDevice);
    }

    /** Registra o actualiza un dispositivo. */
    public Device register(Device device) {
        if (device == null || device.token() == null || device.token().isBlank()) {
            throw new IllegalArgumentException("Device y token son obligatorios.");
        }

        try {
            String json = objectMapper.writeValueAsString(device);
            redis.opsForHash().put(DEVICES_KEY, device.token(), json);
        } catch (Exception ex) {
            log.error("Error guardando dispositivo en Redis (token={}): {}", device.token(), ex.getMessage());
        }

        return device;
    }

    private Optional<Device> parseDevice(Object rawDevice) {
        if (rawDevice == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(rawDevice.toString(), Device.class));
        } catch (Exception ex) {
            log.warn("Error parseando dispositivo desde Redis: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
