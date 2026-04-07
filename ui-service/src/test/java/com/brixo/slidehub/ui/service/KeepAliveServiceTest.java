package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.repository.PresentationSessionRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KeepAliveServiceTest {

    @Mock
    private PresentationSessionRepository sessionRepository;

    @Mock
    private AuthenticatedSessionTracker authenticatedSessionTracker;

    private UserActivityTracker userActivityTracker;

    private HttpServer stateServer;
    private HttpServer aiServer;

    @AfterEach
    void tearDown() {
        if (stateServer != null) {
            stateServer.stop(0);
        }
        if (aiServer != null) {
            aiServer.stop(0);
        }
    }

    @Test
    void ping_whenNoActiveSession_doesNotCallExternalServices() throws Exception {
        AtomicInteger stateCalls = new AtomicInteger(0);
        AtomicInteger aiCalls = new AtomicInteger(0);

        stateServer = startHealthServer(stateCalls);
        aiServer = startHealthServer(aiCalls);

        given(sessionRepository.existsByActiveTrue()).willReturn(false);
        given(authenticatedSessionTracker.hasAuthenticatedSessions()).willReturn(false);

        userActivityTracker = new UserActivityTracker();

        KeepAliveService service = new KeepAliveService(
                sessionRepository,
                authenticatedSessionTracker,
                userActivityTracker,
                baseUrl(stateServer),
                baseUrl(aiServer),
                300000);

        service.ping();
        Thread.sleep(250);

        assertEquals(0, stateCalls.get());
        assertEquals(0, aiCalls.get());
    }

    @Test
    void ping_whenActiveSession_callsStateAndAiHealthEndpoints() throws Exception {
        AtomicInteger stateCalls = new AtomicInteger(0);
        AtomicInteger aiCalls = new AtomicInteger(0);

        stateServer = startHealthServer(stateCalls);
        aiServer = startHealthServer(aiCalls);

        given(sessionRepository.existsByActiveTrue()).willReturn(true);
        given(authenticatedSessionTracker.hasAuthenticatedSessions()).willReturn(false);

        userActivityTracker = new UserActivityTracker();

        KeepAliveService service = new KeepAliveService(
                sessionRepository,
                authenticatedSessionTracker,
                userActivityTracker,
                baseUrl(stateServer),
                baseUrl(aiServer),
                300000);

        service.ping();

        boolean bothCalled = waitUntil(() -> stateCalls.get() >= 1 && aiCalls.get() >= 1, 2000);
        assertTrue(bothCalled, "Los pings a state-service y ai-service no se ejecutaron en el tiempo esperado");
    }

    @Test
    void ping_whenRecentUserActivity_callsStateAndAiHealthEndpointsWithoutActivePresentation() throws Exception {
        AtomicInteger stateCalls = new AtomicInteger(0);
        AtomicInteger aiCalls = new AtomicInteger(0);

        stateServer = startHealthServer(stateCalls);
        aiServer = startHealthServer(aiCalls);

        given(sessionRepository.existsByActiveTrue()).willReturn(false);
        given(authenticatedSessionTracker.hasAuthenticatedSessions()).willReturn(false);

        userActivityTracker = new UserActivityTracker();
        userActivityTracker.markActivityNow();

        KeepAliveService service = new KeepAliveService(
                sessionRepository,
                authenticatedSessionTracker,
                userActivityTracker,
                baseUrl(stateServer),
                baseUrl(aiServer),
                300000);

        service.ping();

        boolean bothCalled = waitUntil(() -> stateCalls.get() >= 1 && aiCalls.get() >= 1, 2000);
        assertTrue(bothCalled,
                "Con actividad reciente de usuario también debe ejecutar keep-alive aunque no haya sesión de presentación");
    }

    @Test
    void ping_whenAuthenticatedUiSession_callsStateAndAiHealthEndpoints() throws Exception {
        AtomicInteger stateCalls = new AtomicInteger(0);
        AtomicInteger aiCalls = new AtomicInteger(0);

        stateServer = startHealthServer(stateCalls);
        aiServer = startHealthServer(aiCalls);

        given(sessionRepository.existsByActiveTrue()).willReturn(false);
        given(authenticatedSessionTracker.hasAuthenticatedSessions()).willReturn(true);

        userActivityTracker = new UserActivityTracker();

        KeepAliveService service = new KeepAliveService(
                sessionRepository,
                authenticatedSessionTracker,
                userActivityTracker,
                baseUrl(stateServer),
                baseUrl(aiServer),
                300000);

        service.ping();

        boolean bothCalled = waitUntil(() -> stateCalls.get() >= 1 && aiCalls.get() >= 1, 2000);
        assertTrue(bothCalled,
                "Con sesión autenticada abierta también debe ejecutar keep-alive aunque no haya actividad reciente");
    }

    private HttpServer startHealthServer(AtomicInteger calls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/actuator/health", new HealthHandler(calls));
        server.start();
        return server;
    }

    private String baseUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private boolean waitUntil(Check check, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (check.eval()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private interface Check {
        boolean eval();
    }

    private static class HealthHandler implements HttpHandler {

        private final AtomicInteger calls;

        private HealthHandler(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            calls.incrementAndGet();
            byte[] body = "{\"status\":\"UP\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
