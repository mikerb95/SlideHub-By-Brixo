package com.brixo.slidehub.ui;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica que el contexto de Spring arranca correctamente sin servicios
 * externos.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "slidehub.state-service.url=http://localhost:8081",
        "slidehub.ai-service.url=http://localhost:8083",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UiServiceApplicationTests {

    @Test
    void contextLoads() {
        // Si no lanza excepción, el contexto arrancó correctamente
    }
}
