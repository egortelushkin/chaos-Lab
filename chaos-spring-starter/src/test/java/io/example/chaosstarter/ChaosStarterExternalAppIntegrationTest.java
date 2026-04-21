package io.example.chaosstarter;

import com.chaosLab.Chaosify;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = ChaosStarterExternalAppIntegrationTest.TestApplication.class,
        properties = {
                "chaos.scenarios.forced.enabled=true",
                "chaos.scenarios.forced.exception.probability=1.0",
                "chaos.scenarios.forced.delay.probability=0.0",
                "chaos.scenarios.forced.delay.max-ms=0"
        }
)
class ChaosStarterExternalAppIntegrationTest {

    @Autowired
    private ProbeService probeService;

    @Test
    void shouldApplyChaosifyAspectInNonChaosLabPackage() {
        RuntimeException error = assertThrows(RuntimeException.class, probeService::call);
        assertTrue(error.getMessage().contains("Chaos injected failure"));
    }

    @SpringBootApplication
    static class TestApplication {
        @Bean
        ProbeService probeService() {
            return new ProbeService();
        }
    }

    static class ProbeService {
        @Chaosify(scenario = "forced")
        String call() {
            return "ok";
        }
    }
}
