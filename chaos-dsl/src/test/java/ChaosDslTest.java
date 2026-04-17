import com.chaosLab.ChaosCiGate;
import com.chaosLab.ChaosExperiment;
import com.chaosLab.ExperimentReport;
import com.chaosLab.PhaseType;
import com.chaosLab.dsl.ChaosDsl;
import com.chaosLab.dsl.ChaosLibCli;
import com.chaosLab.dsl.ChaosDslRunner;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosDslTest {

    @Test
    void testDslParsesAndRunsSuccessfulExperiment() {
        String yaml = """
                experiment:
                  name: checkout_resilience
                  seed: 42
                load:
                  virtualUsers: 2
                  workerThreads: 2
                  thinkTimeMs: 1
                phases:
                  - name: warmup
                    type: WARMUP
                    durationMs: 80
                  - name: fault
                    type: FAULT
                    durationMs: 120
                  - name: recovery
                    type: RECOVERY
                    durationMs: 80
                fault:
                  delayMaxMs: 2
                  delayProbability: 0.2
                  exceptionProbability: 0.0
                  mode: PARALLEL
                invariants:
                  maxErrorRate: 0.05
                  maxP95LatencyMs: 20
                users:
                  steps:
                    - operation: login
                      latencyMs: 1
                      successRate: 1.0
                    - operation: checkout
                      latencyMs: 1
                      successRate: 1.0
                """;

        ChaosExperiment experiment = ChaosDsl.fromYaml(yaml);
        ExperimentReport report = experiment.run();

        assertTrue(report.isPassed());
        assertEquals("checkout_resilience", report.getExperimentName());
        assertEquals(3, report.getPhaseReports().size());
        assertEquals(PhaseType.WARMUP, report.getPhaseReports().get(0).getPhaseType());
        assertEquals(PhaseType.FAULT, report.getPhaseReports().get(1).getPhaseType());
        assertEquals(PhaseType.RECOVERY, report.getPhaseReports().get(2).getPhaseType());
    }

    @Test
    void testDslFailureTriggersCiGate() {
        String yaml = """
                experiment:
                  name: fail_case
                load:
                  virtualUsers: 1
                  workerThreads: 1
                phases:
                  - name: fault
                    type: FAULT
                    durationMs: 150
                invariants:
                  maxErrorRate: 0.1
                users:
                  steps:
                    - operation: unstable
                      successRate: 0.0
                """;

        ChaosExperiment experiment = ChaosDsl.fromYaml(yaml);
        ExperimentReport report = experiment.run();

        assertFalse(report.isPassed());
        assertThrows(IllegalStateException.class, () -> ChaosCiGate.assertPassed(report));
        assertEquals(1, ChaosCiGate.exitCode(report));
    }

    @Test
    void testDslValidatesRequiredFields() {
        String invalidYaml = """
                experiment:
                  name: missing_phases
                users:
                  steps:
                    - operation: op
                      successRate: 1.0
                """;

        assertThrows(IllegalArgumentException.class, () -> ChaosDsl.fromYaml(invalidYaml));
    }

    @Test
    void testRunnerWritesReportWithoutGate() throws Exception {
        String yaml = """
                experiment:
                  name: runner_report
                load:
                  virtualUsers: 1
                  workerThreads: 1
                phases:
                  - name: fault
                    type: FAULT
                    durationMs: 120
                invariants:
                  maxErrorRate: 0.0
                users:
                  steps:
                    - operation: unstable
                      successRate: 0.0
                """;

        Path dslFile = Files.createTempFile("chaos-dsl-", ".yaml");
        Files.writeString(dslFile, yaml);

        Path reportFile = Files.createTempFile("chaos-report-", ".json");
        ExperimentReport report = ChaosDslRunner.run(dslFile, reportFile, false);

        assertFalse(report.isPassed());
        String written = Files.readString(reportFile);
        assertTrue(written.contains("\"experimentName\":\"runner_report\""));
        assertTrue(written.contains("\"resilienceScore\""));
    }

    @Test
    void testDslParsesFaultTargetOperations() {
        String yaml = """
                experiment:
                  name: targeted_fault
                phases:
                  - name: fault
                    type: FAULT
                    durationMs: 80
                fault:
                  delayMaxMs: 1
                  targetOperations:
                    - checkout
                    - payment
                users:
                  steps:
                    - operation: checkout
                      successRate: 1.0
                """;

        ChaosExperiment experiment = ChaosDsl.fromYaml(yaml);
        assertEquals(2, experiment.getFaultTargetOperations().size());
        assertTrue(experiment.getFaultTargetOperations().contains("checkout"));
        assertTrue(experiment.getFaultTargetOperations().contains("payment"));
    }

    @Test
    void testDslNoDuplicateOrderIdsInvariantFailsWhenDuplicatesAppear() {
        String yaml = """
                experiment:
                  name: duplicate_orders_dsl
                load:
                  virtualUsers: 1
                  workerThreads: 1
                phases:
                  - name: fault
                    type: FAULT
                    durationMs: 120
                invariants:
                  noDuplicateOrderIds: true
                users:
                  steps:
                    - operation: checkout
                      successRate: 1.0
                      emitOrderId: true
                      duplicateOrderIdRate: 1.0
                """;

        ExperimentReport report = ChaosDsl.fromYaml(yaml).run();

        assertFalse(report.isPassed());
        assertTrue(report.getMetrics().getDuplicateOrderIds() > 0);
        assertTrue(report.getInvariantResults().stream()
                .anyMatch(result -> "no_duplicate_order_ids".equals(result.getName()) && !result.isPassed()));
    }

    @Test
    void testChaosLibCliWritesArtifactsAndReturnsNonZeroWithoutGate() throws Exception {
        String yaml = """
                experiment:
                  name: cli_failure_case
                load:
                  virtualUsers: 1
                  workerThreads: 1
                phases:
                  - name: fault
                    type: FAULT
                    durationMs: 120
                invariants:
                  maxErrorRate: 0.0
                users:
                  steps:
                    - operation: unstable
                      successRate: 0.0
                """;

        Path dslFile = Files.createTempFile("chaos-cli-", ".yaml");
        Files.writeString(dslFile, yaml);

        Path artifactsDir = Files.createTempDirectory("chaos-artifacts-");
        int exitCode = ChaosLibCli.execute(new String[]{
                "run",
                dslFile.toString(),
                "--artifacts-dir",
                artifactsDir.toString(),
                "--no-gate"
        });

        assertEquals(1, exitCode);
        Path report = artifactsDir.resolve("report.json");
        Path snapshot = artifactsDir.resolve("config-snapshot.yaml");
        Path metadata = artifactsDir.resolve("run-metadata.json");

        assertTrue(Files.exists(report));
        assertTrue(Files.exists(snapshot));
        assertTrue(Files.exists(metadata));

        String reportJson = Files.readString(report);
        assertTrue(reportJson.contains("\"experimentName\":\"cli_failure_case\""));

        String snapshotYaml = Files.readString(snapshot);
        assertTrue(snapshotYaml.contains("cli_failure_case"));

        String metadataJson = Files.readString(metadata);
        assertTrue(metadataJson.contains("\"status\":\"FAIL\""));
        assertTrue(metadataJson.contains("\"dslSha256\":\""));
        assertTrue(metadataJson.contains("\"failedInvariants\""));
    }

    @Test
    void testChaosLibCliReturnsZeroForSuccessfulGate() throws Exception {
        String yaml = """
                experiment:
                  name: cli_success_case
                load:
                  virtualUsers: 1
                  workerThreads: 1
                phases:
                  - name: fault
                    type: FAULT
                    durationMs: 80
                invariants:
                  maxErrorRate: 1.0
                users:
                  steps:
                    - operation: stable
                      successRate: 1.0
                """;

        Path dslFile = Files.createTempFile("chaos-cli-pass-", ".yaml");
        Files.writeString(dslFile, yaml);

        Path reportFile = Files.createTempFile("chaos-cli-report-", ".json");
        int exitCode = ChaosLibCli.execute(new String[]{
                "run",
                dslFile.toString(),
                "--report",
                reportFile.toString()
        });

        assertEquals(0, exitCode);
        String reportJson = Files.readString(reportFile);
        assertNotNull(reportJson);
        assertTrue(reportJson.contains("\"status\":\"PASS\""));
    }

    @Test
    void testDslHttpStepsSupportCaptureAndSessionTemplate() throws Exception {
        AtomicLong idCounter = new AtomicLong(1000);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/orders", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                writeJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            String id = "ord-" + idCounter.incrementAndGet();
            writeJson(exchange, 201, "{\"order\":{\"id\":\"" + id + "\",\"status\":\"CREATED\"}}");
        });
        server.createContext("/orders/", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                writeJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (!path.endsWith("/pay")) {
                writeJson(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            String id = path.substring("/orders/".length(), path.length() - "/pay".length());
            writeJson(exchange, 200, "{\"result\":{\"order\":{\"id\":\"" + id + "\",\"status\":\"PAID\"}}}");
        });

        server.start();
        try {
            int port = server.getAddress().getPort();
            String yaml = """
                    experiment:
                      name: http_checkout_flow
                    load:
                      virtualUsers: 1
                      workerThreads: 1
                      thinkTimeMs: 1
                    phases:
                      - name: fault
                        type: FAULT
                        durationMs: 150
                    invariants:
                      maxErrorRate: 0.0
                      noDuplicateOrderIds: true
                    users:
                      steps:
                        - operation: create_order
                          method: POST
                          url: http://localhost:%d/orders
                          successStatusCodes: [201]
                          capture:
                            orderId: order.id
                        - operation: pay_order
                          method: POST
                          url: http://localhost:%d/orders/${orderId}/pay
                          successStatusCodes: [200]
                          emitOrderIdJsonField: result.order.id
                    """.formatted(port, port);

            ExperimentReport report = ChaosDsl.fromYaml(yaml).run();

            assertTrue(report.isPassed());
            assertTrue(report.getMetrics().getTotalOperations() > 0);
            assertTrue(report.getMetrics().getUniqueOrderIds() > 0);
            assertEquals(0, report.getMetrics().getDuplicateOrderIds());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testChaosLibCliPrometheusCheckAffectsGateAndReport() throws Exception {
        HttpServer prometheus = HttpServer.create(new InetSocketAddress(0), 0);
        prometheus.setExecutor(Executors.newCachedThreadPool());
        prometheus.createContext("/api/v1/query", exchange -> {
            String body = """
                    {
                      "status":"success",
                      "data":{
                        "resultType":"vector",
                        "result":[
                          {"metric":{"job":"demo"},"value":[1713380000,"0.07"]}
                        ]
                      }
                    }
                    """;
            writeJson(exchange, 200, body);
        });
        prometheus.start();
        try {
            int port = prometheus.getAddress().getPort();
            String yaml = """
                    experiment:
                      name: prometheus_gate
                    load:
                      virtualUsers: 1
                      workerThreads: 1
                    phases:
                      - name: fault
                        type: FAULT
                        durationMs: 80
                    invariants:
                      maxErrorRate: 1.0
                    observability:
                      prometheus:
                        baseUrl: http://localhost:%d
                        checks:
                          - name: error_rate_prom
                            query: demo_error_rate
                            operator: <=
                            threshold: 0.03
                    users:
                      steps:
                        - operation: stable
                          successRate: 1.0
                    """.formatted(port);

            Path dslFile = Files.createTempFile("chaos-prom-", ".yaml");
            Files.writeString(dslFile, yaml);

            Path reportFile = Files.createTempFile("chaos-prom-report-", ".json");
            int exitCode = ChaosLibCli.execute(new String[]{
                    "run",
                    dslFile.toString(),
                    "--report",
                    reportFile.toString()
            });

            assertEquals(1, exitCode);
            String reportJson = Files.readString(reportFile);
            assertTrue(reportJson.contains("\"status\":\"FAIL\""));
            assertTrue(reportJson.contains("prometheus.error_rate_prom <= 0.03"));
            assertTrue(reportJson.contains("actual=0.07"));
        } finally {
            prometheus.stop(0);
        }
    }

    @Test
    void testChaosLibCliPrometheusRangeCheckSupportsReducer() throws Exception {
        HttpServer prometheus = HttpServer.create(new InetSocketAddress(0), 0);
        prometheus.setExecutor(Executors.newCachedThreadPool());
        prometheus.createContext("/api/v1/query_range", exchange -> {
            String body = """
                    {
                      "status":"success",
                      "data":{
                        "resultType":"matrix",
                        "result":[
                          {
                            "metric":{"service":"checkout"},
                            "values":[[1713380000,"600"],[1713380060,"950"],[1713380120,"700"]]
                          }
                        ]
                      }
                    }
                    """;
            writeJson(exchange, 200, body);
        });
        prometheus.start();
        try {
            int port = prometheus.getAddress().getPort();
            String yaml = """
                    experiment:
                      name: prometheus_range_gate
                    load:
                      virtualUsers: 1
                      workerThreads: 1
                    phases:
                      - name: fault
                        type: FAULT
                        durationMs: 80
                    invariants:
                      maxErrorRate: 1.0
                    observability:
                      prometheus:
                        baseUrl: http://localhost:%d
                        checks:
                          - name: p95_range
                            query: checkout_p95_latency_ms
                            mode: range
                            rangeSeconds: 300
                            stepSeconds: 60
                            reducer: max
                            operator: <=
                            threshold: 800
                    users:
                      steps:
                        - operation: stable
                          successRate: 1.0
                    """.formatted(port);

            Path dslFile = Files.createTempFile("chaos-prom-range-", ".yaml");
            Files.writeString(dslFile, yaml);

            Path reportFile = Files.createTempFile("chaos-prom-range-report-", ".json");
            int exitCode = ChaosLibCli.execute(new String[]{
                    "run",
                    dslFile.toString(),
                    "--report",
                    reportFile.toString()
            });

            assertEquals(1, exitCode);
            String reportJson = Files.readString(reportFile);
            assertTrue(reportJson.contains("\"status\":\"FAIL\""));
            assertTrue(reportJson.contains("prometheus.p95_range <= 800.0"));
            assertTrue(reportJson.contains("actual=950.0"));
            assertTrue(reportJson.contains("mode=range"));
        } finally {
            prometheus.stop(0);
        }
    }

    @Test
    void testChaosLibCliGrafanaAnnotationsCreatedForPhasesAndRun() throws Exception {
        AtomicInteger annotationCalls = new AtomicInteger();
        CopyOnWriteArrayList<String> authHeaders = new CopyOnWriteArrayList<>();

        HttpServer grafana = HttpServer.create(new InetSocketAddress(0), 0);
        grafana.setExecutor(Executors.newCachedThreadPool());
        grafana.createContext("/api/annotations", exchange -> {
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int id = annotationCalls.incrementAndGet();
            writeJson(exchange, 200, "{\"id\":" + id + "}");
        });
        grafana.start();
        try {
            int port = grafana.getAddress().getPort();
            String yaml = """
                    experiment:
                      name: grafana_annotations
                    load:
                      virtualUsers: 1
                      workerThreads: 1
                    phases:
                      - name: warmup
                        type: WARMUP
                        durationMs: 50
                      - name: fault
                        type: FAULT
                        durationMs: 50
                    invariants:
                      maxErrorRate: 1.0
                    observability:
                      grafana:
                        baseUrl: http://localhost:%d
                        apiToken: test-token
                        tags: [chaoslib, checkout]
                        annotatePhases: true
                        annotateRun: true
                        failOnError: true
                    users:
                      steps:
                        - operation: stable
                          successRate: 1.0
                    """.formatted(port);

            Path dslFile = Files.createTempFile("chaos-grafana-", ".yaml");
            Files.writeString(dslFile, yaml);

            Path artifactsDir = Files.createTempDirectory("chaos-grafana-artifacts-");
            int exitCode = ChaosLibCli.execute(new String[]{
                    "run",
                    dslFile.toString(),
                    "--artifacts-dir",
                    artifactsDir.toString()
            });

            assertEquals(0, exitCode);
            assertEquals(3, annotationCalls.get());
            assertTrue(authHeaders.stream().allMatch("Bearer test-token"::equals));

            String metadata = Files.readString(artifactsDir.resolve("run-metadata.json"));
            assertTrue(metadata.contains("\"grafanaAnnotations\""));
            assertTrue(metadata.contains("\"requested\":3"));
            assertTrue(metadata.contains("\"created\":3"));
        } finally {
            grafana.stop(0);
        }
    }

    @Test
    void testChaosLibCliSpringControlSyncSwitchesScenariosAcrossPhases() throws Exception {
        AtomicInteger switchCalls = new AtomicInteger();
        CopyOnWriteArrayList<String> payloads = new CopyOnWriteArrayList<>();

        HttpServer controlApi = HttpServer.create(new InetSocketAddress(0), 0);
        controlApi.setExecutor(Executors.newCachedThreadPool());
        controlApi.createContext("/chaos/control/scenarios/enable-only", exchange -> {
            payloads.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            switchCalls.incrementAndGet();
            writeJson(exchange, 200, "{\"success\":true}");
        });
        controlApi.start();
        try {
            int port = controlApi.getAddress().getPort();
            String yaml = """
                    experiment:
                      name: spring_phase_sync_ok
                    load:
                      virtualUsers: 1
                      workerThreads: 1
                    phases:
                      - name: warmup
                        type: WARMUP
                        durationMs: 60
                      - name: fault
                        type: FAULT
                        durationMs: 60
                      - name: recovery
                        type: RECOVERY
                        durationMs: 60
                    invariants:
                      maxErrorRate: 1.0
                    runtime:
                      springControl:
                        enabled: true
                        baseUrl: http://localhost:%d
                        failOnError: true
                        disableAllAfterRun: true
                        warmupScenarios: [default]
                        faultScenarios: [stress]
                        recoveryScenarios: [default]
                    users:
                      steps:
                        - operation: stable
                          successRate: 1.0
                    """.formatted(port);

            Path dslFile = Files.createTempFile("chaos-spring-sync-ok-", ".yaml");
            Files.writeString(dslFile, yaml);

            Path artifactsDir = Files.createTempDirectory("chaos-spring-sync-ok-artifacts-");
            int exitCode = ChaosLibCli.execute(new String[]{
                    "run",
                    dslFile.toString(),
                    "--artifacts-dir",
                    artifactsDir.toString()
            });

            assertEquals(0, exitCode);
            assertTrue(switchCalls.get() >= 4);
            assertTrue(payloads.stream().anyMatch(body -> body.contains("\"names\":[\"default\"]")));
            assertTrue(payloads.stream().anyMatch(body -> body.contains("\"names\":[\"stress\"]")));
            assertTrue(payloads.stream().anyMatch(body -> body.contains("\"names\":[]")));

            String metadata = Files.readString(artifactsDir.resolve("run-metadata.json"));
            assertTrue(metadata.contains("\"springPhaseSync\""));
            assertTrue(metadata.contains("\"errors\":[]"));
        } finally {
            controlApi.stop(0);
        }
    }

    @Test
    void testChaosLibCliSpringControlSyncFailOnErrorBlocksGateWhenUnavailable() throws Exception {
        String yaml = """
                experiment:
                  name: spring_phase_sync_fail
                load:
                  virtualUsers: 1
                  workerThreads: 1
                phases:
                  - name: fault
                    type: FAULT
                    durationMs: 100
                invariants:
                  maxErrorRate: 1.0
                runtime:
                  springControl:
                    enabled: true
                    baseUrl: http://localhost:65531
                    timeoutMs: 200
                    failOnError: true
                    faultScenarios: [stress]
                users:
                  steps:
                    - operation: stable
                      successRate: 1.0
                """;

        Path dslFile = Files.createTempFile("chaos-spring-sync-fail-", ".yaml");
        Files.writeString(dslFile, yaml);

        Path reportFile = Files.createTempFile("chaos-spring-sync-fail-report-", ".json");
        int exitCode = ChaosLibCli.execute(new String[]{
                "run",
                dslFile.toString(),
                "--report",
                reportFile.toString()
        });

        assertEquals(1, exitCode);
        String reportJson = Files.readString(reportFile);
        assertTrue(reportJson.contains("\"status\":\"FAIL\""));
        assertTrue(reportJson.contains("spring_control_sync"));
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
