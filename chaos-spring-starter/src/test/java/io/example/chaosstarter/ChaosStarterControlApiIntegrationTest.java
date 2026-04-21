package io.example.chaosstarter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = ChaosStarterControlApiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "chaos.scenarios.default.enabled=true",
                "chaos.scenarios.default.delay.probability=0.0",
                "chaos.scenarios.default.delay.max-ms=0",
                "chaos.scenarios.default.exception.probability=0.0",
                "chaos.control.enabled=true"
        }
)
class ChaosStarterControlApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldExposeScenarioControlEndpointsWhenEnabled() {
        ResponseEntity<String> response = restTemplate.getForEntity("/chaos/control/scenarios", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("default"));
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
