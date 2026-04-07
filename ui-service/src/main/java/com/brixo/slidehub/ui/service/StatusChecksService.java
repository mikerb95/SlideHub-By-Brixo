package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.StatusCheckItem;
import com.brixo.slidehub.ui.model.StatusChecksResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StatusChecksService {

    private final String stateServiceUrl;
    private final String aiServiceUrl;
    private final String gatewayUrl;
    private final String renderUrl;
    private final String redisHost;
    private final int redisPort;
    private final String mongodbUri;
    private final String postgresUrl;
    private final String s3Bucket;
    private final String s3Region;
    private final Duration checkTimeout;
    private final Duration cacheTtl;

    private volatile CachedResponse cachedResponse;

    public StatusChecksService(
            @Value("${slidehub.state-service.url:http://localhost:8081}") String stateServiceUrl,
            @Value("${slidehub.ai-service.url:http://localhost:8083}") String aiServiceUrl,
            @Value("${slidehub.gateway.url:http://localhost:8080}") String gatewayUrl,
            @Value("${slidehub.render.url:}") String renderUrl,
            @Value("${slidehub.redis.host:${REDIS_HOST:}}") String redisHost,
            @Value("${slidehub.redis.port:${REDIS_PORT:6379}}") int redisPort,
            @Value("${slidehub.mongodb.uri:${MONGODB_URI:}}") String mongodbUri,
            @Value("${slidehub.postgres.url:${DATABASE_URL:}}") String postgresUrl,
            @Value("${aws.s3.bucket:${AWS_S3_BUCKET:}}") String s3Bucket,
            @Value("${aws.s3.region:${AWS_REGION:us-east-1}}") String s3Region,
            @Value("${slidehub.status.check.timeout-ms:2500}") long timeoutMs,
            @Value("${slidehub.status.cache-ttl-ms:1000}") long cacheTtlMs) {
        this.stateServiceUrl = stateServiceUrl;
        this.aiServiceUrl = aiServiceUrl;
        this.gatewayUrl = gatewayUrl;
        this.renderUrl = renderUrl;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.mongodbUri = mongodbUri;
        this.postgresUrl = postgresUrl;
        this.s3Bucket = s3Bucket;
        this.s3Region = s3Region;
        this.checkTimeout = Duration.ofMillis(timeoutMs);
        this.cacheTtl = Duration.ofMillis(cacheTtlMs);
    }

    public StatusChecksResponse getChecks() {
        CachedResponse snapshot = this.cachedResponse;
        Instant now = Instant.now();
        if (snapshot != null && now.isBefore(snapshot.expiresAt())) {
            return snapshot.response();
        }

        synchronized (this) {
            snapshot = this.cachedResponse;
            now = Instant.now();
            if (snapshot != null && now.isBefore(snapshot.expiresAt())) {
                return snapshot.response();
            }

            StatusChecksResponse fresh = buildChecks(now);
            this.cachedResponse = new CachedResponse(fresh, now.plus(cacheTtl));
            return fresh;
        }
    }

    StatusChecksResponse buildChecks(Instant timestamp) {
        List<StatusCheckItem> checks = new ArrayList<>();

        checks.add(checkHttp("state-service", normalizeBaseUrl(stateServiceUrl) + "/actuator/health", timestamp));
        checks.add(checkHttp("ai-service", normalizeBaseUrl(aiServiceUrl) + "/api/ai/notes/health", timestamp));
        checks.add(checkHttp("gateway", normalizeBaseUrl(gatewayUrl) + "/actuator/health", timestamp));

        if (isConfigured(renderUrl)) {
            checks.add(checkHttp("render", normalizeBaseUrl(renderUrl), timestamp));
        } else {
            checks.add(notConfigured("render", timestamp));
        }

        if (isConfigured(redisHost)) {
            checks.add(checkTcp("redis", redisHost.trim(), redisPort, timestamp));
        } else {
            checks.add(notConfigured("redis", timestamp));
        }

        HostPort mongoHostPort = parseMongoHostPort(mongodbUri);
        if (mongoHostPort != null) {
            checks.add(checkTcp("mongodb", mongoHostPort.host(), mongoHostPort.port(), timestamp));
        } else {
            checks.add(notConfigured("mongodb", timestamp));
        }

        HostPort postgresHostPort = parsePostgresHostPort(postgresUrl);
        if (postgresHostPort != null) {
            checks.add(checkTcp("postgres", postgresHostPort.host(), postgresHostPort.port(), timestamp));
        } else {
            checks.add(notConfigured("postgres", timestamp));
        }

        if (isConfigured(s3Bucket) && isConfigured(s3Region)) {
            String s3Host = "%s.s3.%s.amazonaws.com".formatted(s3Bucket.trim(), s3Region.trim());
            checks.add(checkTcp("aws-s3", s3Host, 443, timestamp));
        } else {
            checks.add(notConfigured("aws-s3", timestamp));
        }

        return new StatusChecksResponse(timestamp, checks);
    }

    StatusCheckItem checkHttp(String name, String url, Instant timestamp) {
        Instant start = Instant.now();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(checkTimeout)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(checkTimeout)
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;

            return new StatusCheckItem(
                    name,
                    ok ? "ok" : "down",
                    latencyMs,
                    timestamp,
                    "HTTP " + response.statusCode());
        } catch (Exception ex) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            return new StatusCheckItem(name, "down", latencyMs > 0 ? latencyMs : null, timestamp, sanitize(ex));
        }
    }

    StatusCheckItem checkTcp(String name, String host, int port, Instant timestamp) {
        Instant start = Instant.now();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) checkTimeout.toMillis());
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            return new StatusCheckItem(name, "ok", latencyMs, timestamp, "TCP connected");
        } catch (IOException ex) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            return new StatusCheckItem(name, "down", latencyMs > 0 ? latencyMs : null, timestamp, sanitize(ex));
        }
    }

    private StatusCheckItem notConfigured(String name, Instant timestamp) {
        return new StatusCheckItem(name, "down", null, timestamp, "not configured");
    }

    private String sanitize(Exception ex) {
        String simple = ex.getClass().getSimpleName();
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            return simple;
        }
        int cut = Math.min(msg.length(), 120);
        return simple + ": " + msg.substring(0, cut);
    }

    private String normalizeBaseUrl(String url) {
        if (!isConfigured(url)) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    private HostPort parseMongoHostPort(String uri) {
        if (!isConfigured(uri)) {
            return null;
        }
        String clean = uri.trim()
                .replace("mongodb://", "")
                .replace("mongodb+srv://", "");

        int atIndex = clean.indexOf('@');
        if (atIndex >= 0) {
            clean = clean.substring(atIndex + 1);
        }
        int slashIndex = clean.indexOf('/');
        if (slashIndex >= 0) {
            clean = clean.substring(0, slashIndex);
        }
        String firstHost = clean.split(",")[0];
        if (firstHost.isBlank()) {
            return null;
        }

        String[] hostPort = firstHost.split(":");
        if (hostPort.length == 2) {
            return new HostPort(hostPort[0], parsePort(hostPort[1], 27017));
        }
        return new HostPort(hostPort[0], 27017);
    }

    private HostPort parsePostgresHostPort(String jdbcUrl) {
        if (!isConfigured(jdbcUrl) || !jdbcUrl.contains("//")) {
            return null;
        }
        String afterScheme = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2);
        int slashIndex = afterScheme.indexOf('/');
        String hostAndPort = slashIndex >= 0 ? afterScheme.substring(0, slashIndex) : afterScheme;
        int atIndex = hostAndPort.lastIndexOf('@');
        if (atIndex >= 0) {
            hostAndPort = hostAndPort.substring(atIndex + 1);
        }
        String[] parts = hostAndPort.split(":");
        if (parts.length == 2) {
            return new HostPort(parts[0], parsePort(parts[1], 5432));
        }
        if (parts.length == 1 && !parts[0].isBlank()) {
            return new HostPort(parts[0], 5432);
        }
        return null;
    }

    private int parsePort(String maybePort, int defaultPort) {
        try {
            return Integer.parseInt(maybePort.trim());
        } catch (Exception ignored) {
            return defaultPort;
        }
    }

    private record HostPort(String host, int port) {
    }

    private record CachedResponse(StatusChecksResponse response, Instant expiresAt) {
    }
}
