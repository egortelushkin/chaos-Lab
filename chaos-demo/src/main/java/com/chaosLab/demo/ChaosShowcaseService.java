package com.chaosLab.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chaosLab.dsl.ChaosLibCli;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChaosShowcaseService {

    private static final Logger log = LoggerFactory.getLogger(ChaosShowcaseService.class);
    private static final String CREATE_ORDER_OP = "create_order";
    private static final String PAY_ORDER_OP = "pay_order";

    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ChaosShowcaseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ShowcaseRunResult run(String baseUrl, ShowcaseMode mode) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Showcase run is already in progress");
        }

        try {
            String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
            PhaseDurations durations = mode == ShowcaseMode.FULL
                    ? new PhaseDurations(30_000L, 60_000L, 30_000L)
                    : new PhaseDurations(3_000L, 6_000L, 3_000L);

            Path runDir = Files.createTempDirectory("chaos-showcase-");
            Path dslPath = runDir.resolve("showcase-runtime.yaml");
            Path artifactsDir = runDir.resolve("artifacts");
            Files.writeString(dslPath, renderDsl(normalizedBaseUrl, durations), StandardCharsets.UTF_8);

            int exitCode = ChaosLibCli.execute(new String[]{
                    "run",
                    dslPath.toString(),
                    "--artifacts-dir",
                    artifactsDir.toString()
            });

            Path reportPath = artifactsDir.resolve("report.json");
            Path metadataPath = artifactsDir.resolve("run-metadata.json");
            JsonNode report = objectMapper.readTree(Files.readString(reportPath, StandardCharsets.UTF_8));
            JsonNode metadata = objectMapper.readTree(Files.readString(metadataPath, StandardCharsets.UTF_8));

            List<PhaseSnapshot> phaseSnapshots = parsePhases(report.path("phaseReports"));
            List<String> failedInvariants = parseFailedInvariants(report.path("invariantResults"));
            List<String> springSyncErrors = parseStringArray(metadata.path("springPhaseSync").path("errors"));

            ShowcaseRunResult result = new ShowcaseRunResult(
                    mode.name().toLowerCase(Locale.ROOT),
                    exitCode,
                    report.path("status").asText("UNKNOWN"),
                    report.path("resilienceScore").asDouble(0.0),
                    report.path("metrics").path("totalOperations").asLong(0),
                    report.path("metrics").path("errorRate").asDouble(0.0),
                    report.path("metrics").path("p95LatencyMs").asDouble(0.0),
                    report.path("metrics").path("duplicateOrderIds").asLong(0),
                    failedInvariants,
                    phaseSnapshots,
                    metadata.path("springPhaseSync").path("requestedSwitches").asInt(0),
                    metadata.path("springPhaseSync").path("successfulSwitches").asInt(0),
                    springSyncErrors,
                    dslPath.toString(),
                    reportPath.toString(),
                    metadataPath.toString()
            );

            logSummary(result);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run showcase", e);
        } finally {
            running.set(false);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String renderDsl(String baseUrl, PhaseDurations d) {
        return """
                experiment:
                  name: spring_boot_showcase
                  seed: 20260421

                load:
                  virtualUsers: 24
                  workerThreads: 8
                  thinkTimeMs: 40

                phases:
                  - name: baseline
                    type: WARMUP
                    durationMs: %d
                  - name: fault
                    type: FAULT
                    durationMs: %d
                  - name: recovery
                    type: RECOVERY
                    durationMs: %d

                fault:
                  delayMaxMs: 20
                  delayProbability: 0.2
                  exceptionProbability: 0.05
                  targetOperations:
                    - %s
                  mode: PARALLEL
                  seed: 1337

                runtime:
                  springControl:
                    enabled: true
                    baseUrl: %s
                    timeoutMs: 3000
                    failOnError: true
                    disableAllAfterRun: true
                    warmupScenarios: [default]
                    faultScenarios: [stress]
                    recoveryScenarios: [default]

                invariants:
                  maxErrorRate: 0.40
                  maxP95LatencyMs: 2000
                  noDuplicateOrderIds: true

                users:
                  steps:
                    - operation: %s
                      method: POST
                      url: %s/orders?price=100
                      successStatusCodes: [200]
                      capture:
                        orderId: id
                    - operation: %s
                      method: POST
                      url: %s/orders/${orderId}/pay
                      successStatusCodes: [200]
                      emitOrderIdFromSession: orderId
                """.formatted(
                d.warmupMs(),
                d.faultMs(),
                d.recoveryMs(),
                PAY_ORDER_OP,
                baseUrl,
                CREATE_ORDER_OP,
                baseUrl,
                PAY_ORDER_OP,
                baseUrl
        );
    }

    private static List<PhaseSnapshot> parsePhases(JsonNode phaseReportsNode) {
        List<PhaseSnapshot> phases = new ArrayList<>();
        if (!phaseReportsNode.isArray()) {
            return phases;
        }
        for (JsonNode phaseNode : phaseReportsNode) {
            JsonNode metrics = phaseNode.path("metrics");
            phases.add(new PhaseSnapshot(
                    phaseNode.path("phaseName").asText("unknown"),
                    phaseNode.path("phaseType").asText("UNKNOWN"),
                    metrics.path("totalOperations").asLong(0),
                    metrics.path("errorRate").asDouble(0.0),
                    metrics.path("p95LatencyMs").asDouble(0.0),
                    metrics.path("avgLatencyMs").asDouble(0.0)
            ));
        }
        return phases;
    }

    private static List<String> parseFailedInvariants(JsonNode invariantsNode) {
        List<String> failed = new ArrayList<>();
        if (!invariantsNode.isArray()) {
            return failed;
        }
        for (JsonNode invariantNode : invariantsNode) {
            if (!invariantNode.path("passed").asBoolean(true)) {
                String name = invariantNode.path("name").asText("unknown");
                String details = invariantNode.path("details").asText("");
                failed.add(name + (details.isBlank() ? "" : " :: " + details));
            }
        }
        return failed;
    }

    private static List<String> parseStringArray(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (!node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            result.add(item.asText());
        }
        return result;
    }

    private void logSummary(ShowcaseRunResult result) {
        log.info("Chaos showcase: mode={}, status={}, exitCode={}, score={}",
                result.mode(), result.status(), result.exitCode(), result.resilienceScore());
        log.info("Totals: operations={}, errorRate={}, p95LatencyMs={}, duplicates={}",
                result.totalOperations(), result.errorRate(), result.p95LatencyMs(), result.duplicateOrderIds());
        for (PhaseSnapshot phase : result.phases()) {
            log.info("Phase {} ({}): ops={}, errorRate={}, p95LatencyMs={}, avgLatencyMs={}",
                    phase.phaseName(), phase.phaseType(), phase.totalOperations(),
                    phase.errorRate(), phase.p95LatencyMs(), phase.avgLatencyMs());
        }
        if (!result.failedInvariants().isEmpty()) {
            log.warn("Failed invariants: {}", result.failedInvariants());
        }
        if (!result.springSyncErrors().isEmpty()) {
            log.warn("Spring sync errors: {}", result.springSyncErrors());
        }
        log.info("Artifacts: report={}, metadata={}", result.reportPath(), result.metadataPath());
    }

    private record PhaseDurations(long warmupMs, long faultMs, long recoveryMs) {
    }

    public record PhaseSnapshot(
            String phaseName,
            String phaseType,
            long totalOperations,
            double errorRate,
            double p95LatencyMs,
            double avgLatencyMs
    ) {
    }

    public record ShowcaseRunResult(
            String mode,
            int exitCode,
            String status,
            double resilienceScore,
            long totalOperations,
            double errorRate,
            double p95LatencyMs,
            long duplicateOrderIds,
            List<String> failedInvariants,
            List<PhaseSnapshot> phases,
            int springPhaseSwitchesRequested,
            int springPhaseSwitchesSuccessful,
            List<String> springSyncErrors,
            String dslPath,
            String reportPath,
            String metadataPath
    ) {
    }
}
