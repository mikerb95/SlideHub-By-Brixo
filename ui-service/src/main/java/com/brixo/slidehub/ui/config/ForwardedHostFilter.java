package com.brixo.slidehub.ui.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * En producción, el gateway proxy requests a ui-service vía su URL interna
 * (.onrender.com). Spring Security usa el Host header para generar redirects,
 * lo que causa que el usuario termine en el subdominio interno.
 *
 * Este filtro inyecta X-Forwarded-Host y X-Forwarded-Proto con el dominio
 * público para que ForwardedHeaderFilter (activado por
 * server.forward-headers-strategy=framework) resuelva el host correcto.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "slidehub.base-url")
public class ForwardedHostFilter extends OncePerRequestFilter {

    private final String host;
    private final String proto;

    public ForwardedHostFilter(@Value("${slidehub.base-url}") String baseUrl) {
        // Parse "https://slide.lat" → host="slide.lat", proto="https"
        if (baseUrl.contains("://")) {
            this.proto = baseUrl.substring(0, baseUrl.indexOf("://"));
            this.host = baseUrl.substring(baseUrl.indexOf("://") + 3);
        } else {
            this.proto = "https";
            this.host = baseUrl;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if ("X-Forwarded-Host".equalsIgnoreCase(name)) return host;
                if ("X-Forwarded-Proto".equalsIgnoreCase(name)) return proto;
                if ("X-Forwarded-Port".equalsIgnoreCase(name)) return "443";
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if ("X-Forwarded-Host".equalsIgnoreCase(name)) return Collections.enumeration(List.of(host));
                if ("X-Forwarded-Proto".equalsIgnoreCase(name)) return Collections.enumeration(List.of(proto));
                if ("X-Forwarded-Port".equalsIgnoreCase(name)) return Collections.enumeration(List.of("443"));
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                if (!names.contains("X-Forwarded-Host")) names.add("X-Forwarded-Host");
                if (!names.contains("X-Forwarded-Proto")) names.add("X-Forwarded-Proto");
                if (!names.contains("X-Forwarded-Port")) names.add("X-Forwarded-Port");
                return Collections.enumeration(names);
            }
        };
        filterChain.doFilter(wrapped, response);
    }
}
