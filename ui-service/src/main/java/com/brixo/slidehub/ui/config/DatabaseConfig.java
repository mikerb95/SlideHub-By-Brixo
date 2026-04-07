package com.brixo.slidehub.ui.config;

import javax.sql.DataSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Transforma URLs de base de datos en formato libpq (postgres://...)
 * al formato JDBC requerido por HikariCP (jdbc:postgresql://...).
 *
 * Plataformas como Aiven, Heroku y Render suelen proveer el DSN en formato
 * libpq. El driver JDBC de PostgreSQL no acepta ese formato directamente.
 *
 * Ejemplos de transformación:
 * postgres://user:pass@host:port/db?sslmode=require
 * → jdbc:postgresql://host:port/db?user=user&password=pass&sslmode=require
 *
 * postgresql://user:pass@host:port/db
 * → jdbc:postgresql://host:port/db?user=user&password=pass
 *
 * Si la URL ya comienza con jdbc:, se usa tal cual (sin transformación).
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    public DataSource dataSource() {
        JdbcConnectionInfo connectionInfo = parseConnectionInfo(databaseUrl);
        String jdbcUrl = connectionInfo.jdbcUrl();
        log.info("DataSource URL scheme: {}",
                jdbcUrl.substring(0, Math.min(jdbcUrl.indexOf("://") + 3, jdbcUrl.length())));

        DataSourceBuilder<?> builder = DataSourceBuilder.create()
                .url(jdbcUrl)
                .driverClassName(driverClassName);

        if (connectionInfo.username() != null && !connectionInfo.username().isBlank()) {
            builder.username(connectionInfo.username());
        }
        if (connectionInfo.password() != null) {
            builder.password(connectionInfo.password());
        }

        return builder.build();
    }

    /**
     * Configura Flyway explícitamente para que use el DataSource custom.
     * Sin esto, FlywayAutoConfiguration puede no ejecutar las migraciones
     * cuando el DataSource se define manualmente en un @Bean.
     */
    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource,
                         @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
                         @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate,
                         @Value("${spring.flyway.default-schema:#{null}}") String defaultSchema,
                         @Value("${spring.flyway.schemas:#{null}}") String schemas) {
        var config = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(baselineOnMigrate);

        if (defaultSchema != null && !defaultSchema.isBlank()) {
            config.defaultSchema(defaultSchema);
        }
        if (schemas != null && !schemas.isBlank()) {
            config.schemas(schemas.split(","));
        }

        Flyway flyway = config.load();
        log.info("Flyway configured explicitly with custom DataSource");
        return flyway;
    }

    /**
     * Asegura que JPA (EntityManagerFactory) espere a que Flyway termine
     * antes de validar el esquema.
     */
    @Bean
    public static BeanFactoryPostProcessor flywayDependencyPostProcessor() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                beanFactory.getBeanDefinition("entityManagerFactory")
                        .setDependsOn(new String[]{"flyway"});
            }
        };
    }

    /**
     * Convierte URL de formato libpq a JDBC si es necesario.
     * Si ya es jdbc:..., la retorna sin cambios.
     */
    String convertToJdbcUrl(String url) {
        return parseConnectionInfo(url).jdbcUrl();
    }

    private JdbcConnectionInfo parseConnectionInfo(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL no puede estar vacía");
        }

        // Ya es formato JDBC — no tocar
        if (url.startsWith("jdbc:")) {
            return new JdbcConnectionInfo(url, null, null);
        }

        // Formato libpq: postgres://user:pass@host:port/db?params
        // o: postgresql://user:pass@host:port/db?params
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            return convertLibpqToJdbc(url);
        }

        // H2 u otros formatos de desarrollo
        return new JdbcConnectionInfo(url, null, null);
    }

    private JdbcConnectionInfo convertLibpqToJdbc(String url) {
        URI uri = URI.create(url);
        String authority = uri.getRawAuthority();
        String rawUserInfo = uri.getRawUserInfo();

        if (authority == null || authority.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL inválida: falta host o authority");
        }

        if (rawUserInfo != null && !rawUserInfo.isBlank()) {
            authority = authority.substring(rawUserInfo.length() + 1);
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                .append(authority);

        if (uri.getRawPath() != null) {
            jdbcUrl.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            jdbcUrl.append('?').append(uri.getRawQuery());
        }

        String username = null;
        String password = null;

        if (rawUserInfo != null && !rawUserInfo.isBlank()) {
            int colonIndex = rawUserInfo.indexOf(':');
            if (colonIndex != -1) {
                username = decode(rawUserInfo.substring(0, colonIndex));
                password = decode(rawUserInfo.substring(colonIndex + 1));
            } else {
                username = decode(rawUserInfo);
            }
        }

        log.info("Converted libpq URL to JDBC format (host: {})",
                uri.getHost() != null ? uri.getHost() : authority);

        return new JdbcConnectionInfo(jdbcUrl.toString(), username, password);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record JdbcConnectionInfo(String jdbcUrl, String username, String password) {
    }
}
