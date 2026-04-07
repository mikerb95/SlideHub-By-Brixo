package com.brixo.slidehub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.function.Function;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * Configuración de rutas del API Gateway (AGENTS.md §2.4).
 *
 * ORDEN IMPORTANTE — evaluado de menor a mayor número:
 * /api/ai/** → ai-service:8083 (Order 1)
 * /api/presentations/** → ui-service:8082 (Order 2) — ANTES del catch-all de
 * state
 * /api/** → state-service:8081 (Order 3)
 * /auth/**, /slides, /presenter, /presentations/**, etc. → ui-service:8082
 * (Order 4)
 * /presentation/** → ui-service:8082 (Order 5)
 */
@Configuration
public class RoutesConfig {

        @Value("${slidehub.ai-service.url:http://localhost:8083}")
        private String aiServiceUrl;

        @Value("${slidehub.state-service.url:http://localhost:8081}")
        private String stateServiceUrl;

        @Value("${slidehub.ui-service.url:http://localhost:8082}")
        private String uiServiceUrl;

        @Value("${slidehub.base-url:}")
        private String baseUrl;

        /**
         * Construye un before-filter que inyecta X-Forwarded-Host/Proto/Port
         * para que el servicio downstream resuelva el dominio público (slide.lat)
         * en vez del hostname interno de Render.
         * Si baseUrl está vacío (desarrollo local), no inyecta nada.
         */
        private Function<ServerRequest, ServerRequest> forwardedHeaders() {
                if (baseUrl == null || baseUrl.isBlank()) {
                        return Function.identity();
                }
                String proto = baseUrl.contains("://") ? baseUrl.substring(0, baseUrl.indexOf("://")) : "https";
                String host = baseUrl.contains("://") ? baseUrl.substring(baseUrl.indexOf("://") + 3) : baseUrl;
                return setRequestHeader("X-Forwarded-Host", host)
                        .andThen(setRequestHeader("X-Forwarded-Proto", proto))
                        .andThen(setRequestHeader("X-Forwarded-Port", "https".equals(proto) ? "443" : "80"));
        }

        /** IA routes — DEBE evaluarse antes que /api/** (Order=1) */
        @Bean
        @Order(1)
        public RouterFunction<ServerResponse> aiRoutes() {
                return route("ai-service-routes")
                                .route(RequestPredicates.path("/api/ai/**"), http())
                                .filter(uri(aiServiceUrl))
                                .build();
        }

        /**
         * Presentations API → ui-service (Order=2).
         * DEBE ir ANTES que /api/** (Order=3) para no caer en state-service.
         */
        @Bean
        @Order(2)
        public RouterFunction<ServerResponse> presentationApiRoutes() {
                return route("presentation-api-routes")
                                .route(RequestPredicates.path("/api/presentations/**"), http())
                                .before(forwardedHeaders())
                                .filter(uri(uiServiceUrl))
                                .build();
        }

        /** State routes (Order=3) */
        @Bean
        @Order(3)
        public RouterFunction<ServerResponse> stateRoutes() {
                return route("state-service-routes")
                                .route(RequestPredicates.path("/api/**"), http())
                                .filter(uri(stateServiceUrl))
                                .build();
        }

        /** UI application routes + auth + OAuth2 (Order=4) */
        @Bean
        @Order(4)
        public RouterFunction<ServerResponse> uiRoutes() {
                return route("ui-service-routes")
                                .route(
                                                RequestPredicates.path("/")
                                                                .or(RequestPredicates.path("/auth/**"))
                                                                .or(RequestPredicates.path("/oauth2/**")) // /oauth2/authorization/{provider}
                                                                .or(RequestPredicates.path("/login/oauth2/**")) // /login/oauth2/code/{provider}
                                                                .or(RequestPredicates.path("/slides"))
                                                                .or(RequestPredicates.path("/remote"))
                                                                .or(RequestPredicates.path("/presenter"))
                                                                .or(RequestPredicates.path("/main-panel"))
                                                                .or(RequestPredicates.path("/demo"))
                                                                .or(RequestPredicates.path("/showcase"))
                                                                .or(RequestPredicates.path("/deploy-tutor"))
                                                                .or(RequestPredicates.path("/status"))
                                                                .or(RequestPredicates.path("/status/api/checks"))
                                                                .or(RequestPredicates.path("/calidad"))
                                                                .or(RequestPredicates.path("/presentations/**"))
                                                                .or(RequestPredicates.path("/css/**"))
                                                                .or(RequestPredicates.path("/js/**"))
                                                                .or(RequestPredicates.path("/favicon.ico")), // Fase 2
                                                http())
                                .before(forwardedHeaders())
                                .filter(uri(uiServiceUrl))
                                .build();
        }

        /** Presentation static assets (HU-013, Order=5) */
        @Bean
        @Order(5)
        public RouterFunction<ServerResponse> presentationRoutes() {
                return route("presentation-routes")
                                .route(RequestPredicates.path("/presentation/**"), http())
                                .before(forwardedHeaders())
                                .filter(uri(uiServiceUrl))
                                .build();
        }
}
