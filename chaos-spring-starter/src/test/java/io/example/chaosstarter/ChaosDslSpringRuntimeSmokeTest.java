package io.example.chaosstarter;

import com.chaosLab.Chaosify;
import com.chaosLab.dsl.ChaosLibCli;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = ChaosDslSpringRuntimeSmokeTest.RuntimeTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "chaos.control.enabled=true",
                "chaos.scenarios.default.enabled=true",
                "chaos.scenarios.default.delay.probability=0.0",
                "chaos.scenarios.default.delay.max-ms=0",
                "chaos.scenarios.default.exception.probability=0.0",
                "chaos.scenarios.stress.enabled=true",
                "chaos.scenarios.stress.delay.probability=0.0",
                "chaos.scenarios.stress.delay.max-ms=0",
                "chaos.scenarios.stress.exception.probability=0.2"
        }
)
class ChaosDslSpringRuntimeSmokeTest {

    @LocalServerPort
    private int port;

    @Test
    void shouldRunRuntimeDslWithSpringPhaseControl() throws IOException {
        String dslYaml = """
                experiment:
                  name: smoke_runtime_control
                  seed: 42

                load:
                  virtualUsers: 4
                  workerThreads: 2
                  thinkTimeMs: 20

                phases:
                  - name: warmup
                    type: WARMUP
                    durationMs: 400
                  - name: fault
                    type: FAULT
                    durationMs: 600
                  - name: recovery
                    type: RECOVERY
                    durationMs: 400

                fault:
                  delayMaxMs: 1
                  delayProbability: 0.0
                  exceptionProbability: 0.0
                  targetOperations:
                    - pay_order
                  mode: PARALLEL
                  seed: 1337

                runtime:
                  springControl:
                    enabled: true
                    baseUrl: http://localhost:%d
                    timeoutMs: 2000
                    failOnError: true
                    disableAllAfterRun: true
                    warmupScenarios: [default]
                    faultScenarios: [stress]
                    recoveryScenarios: [default]

                invariants:
                  maxErrorRate: 1.0
                  maxP95LatencyMs: 5000
                  noDuplicateOrderIds: true

                users:
                  steps:
                    - operation: create_order
                      method: POST
                      url: http://localhost:%d/orders?price=100
                      successStatusCodes: [200]
                      capture:
                        orderId: id
                    - operation: pay_order
                      method: POST
                      url: http://localhost:%d/orders/${orderId}/pay
                      successStatusCodes: [200]
                      emitOrderIdFromSession: orderId
                """.formatted(port, port, port);

        Path dslFile = Files.createTempFile("chaos-runtime-", ".yaml");
        Path artifactsDir = Files.createTempDirectory("chaos-runtime-artifacts-");
        Files.writeString(dslFile, dslYaml, StandardCharsets.UTF_8);

        int exitCode = ChaosLibCli.execute(new String[]{
                "run",
                dslFile.toString(),
                "--artifacts-dir",
                artifactsDir.toString()
        });

        assertEquals(0, exitCode);

        String metadata = Files.readString(artifactsDir.resolve("run-metadata.json"), StandardCharsets.UTF_8);
        assertTrue(metadata.contains("\"springPhaseSync\""));
        assertTrue(metadata.contains("\"requestedSwitches\""));
        assertTrue(metadata.contains("\"successfulSwitches\""));
        assertTrue(metadata.contains("\"errors\":[]"));
    }

    @SpringBootApplication
    static class RuntimeTestApplication {
    }

    @RestController
    @RequestMapping("/orders")
    static class OrderController {
        private final OrderService service;

        OrderController(OrderService service) {
            this.service = service;
        }

        @PostMapping
        Order create(@RequestParam double price) {
            return service.create(price);
        }

        @PostMapping("/{id}/pay")
        Order pay(@PathVariable String id) {
            return service.pay(id);
        }
    }

    @Service
    static class OrderService {
        private final Map<String, Order> storage = new ConcurrentHashMap<>();

        @Chaosify(scenario = "default")
        Order create(double price) {
            String id = UUID.randomUUID().toString();
            Order order = new Order(id, price, "CREATED");
            storage.put(id, order);
            return order;
        }

        @Chaosify(scenario = "stress")
        Order pay(String id) {
            Order order = storage.get(id);
            if (order == null) {
                throw new IllegalArgumentException("Order not found: " + id);
            }
            order.setStatus("PAID");
            return order;
        }
    }

    static class Order {
        private String id;
        private double price;
        private String status;

        Order(String id, double price, String status) {
            this.id = id;
            this.price = price;
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public double getPrice() {
            return price;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
