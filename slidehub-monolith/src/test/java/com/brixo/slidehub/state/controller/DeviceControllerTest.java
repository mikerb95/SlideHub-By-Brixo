package com.brixo.slidehub.state.controller;

import com.brixo.slidehub.state.model.Device;
import com.brixo.slidehub.state.model.RegisterDeviceRequest;
import com.brixo.slidehub.state.service.DeviceRegistryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DeviceControllerTest {

    private final DeviceRegistryService deviceRegistryService = mock(DeviceRegistryService.class);
    private final DeviceController deviceController = new DeviceController(deviceRegistryService);

    @Test
    void registerDevice_whenPayloadIsValid_returnsRegisteredDevice() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("X-Forwarded-For")).willReturn("203.0.113.10, 10.0.0.1");

        RegisterDeviceRequest payload = new RegisterDeviceRequest("Tablet Principal", "tablet", "abc-123");
        Device stored = new Device("Tablet Principal", "tablet", "abc-123", "203.0.113.10", LocalDateTime.now());
        given(deviceRegistryService.register(org.mockito.ArgumentMatchers.any(Device.class))).willReturn(stored);

        ResponseEntity<Device> response = deviceController.registerDevice(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isEqualTo("abc-123");
        assertThat(response.getBody().lastIp()).isEqualTo("203.0.113.10");
        verify(deviceRegistryService).register(org.mockito.ArgumentMatchers.any(Device.class));
    }

    @Test
    void heartbeat_whenTokenMissing_returnsBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RegisterDeviceRequest payload = new RegisterDeviceRequest("Phone", "mobile", "  ");

        ResponseEntity<Device> response = deviceController.heartbeat(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(deviceRegistryService);
    }

    @Test
    void heartbeat_whenForwardedHeaderMissing_usesRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("X-Forwarded-For")).willReturn(null);
        given(request.getRemoteAddr()).willReturn("10.10.0.8");

        RegisterDeviceRequest payload = new RegisterDeviceRequest("Remote", "mobile", "tok-999");
        Device stored = new Device("Remote", "mobile", "tok-999", "10.10.0.8", LocalDateTime.now());
        given(deviceRegistryService.register(org.mockito.ArgumentMatchers.any(Device.class))).willReturn(stored);

        ResponseEntity<Device> response = deviceController.heartbeat(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().lastIp()).isEqualTo("10.10.0.8");
    }
}
