package com.chaosLab.dsl;

import com.chaosLab.ChaosCiGate;
import com.chaosLab.ChaosExperiment;
import com.chaosLab.ExperimentReport;
import com.chaosLab.ExperimentReportJson;
import com.chaosLab.ExperimentStatus;
import com.chaosLab.InvariantResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ChaosLibCli {

    private ChaosLibCli() {
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    public static int execute(String[] args) {
        CliOptions options = CliOptions.parse(args);
        if (!"run".equals(options.command)) {
            throw new IllegalArgumentException("Unknown command: " + options.command + ". " + usage());
        }

        return runCommand(options.runOptions);
    }

    private static int runCommand(RunOptions options) {
        Path resolvedDslPath = RunArtifactsWriter.resolveDslPath(options.dslPath);
        String yamlText = RunArtifactsWriter.readDslYaml(resolvedDslPath);
        String dslSha256 = RunArtifactsWriter.sha256(yamlText);
        PrometheusObservability.PrometheusConfig prometheusConfig = PrometheusObservability.parseFromYaml(yamlText);
        GrafanaObservability.GrafanaConfig grafanaConfig = GrafanaObservability.parseFromYaml(yamlText);
        SpringPhaseControlSync.SpringControlConfig springControlConfig = SpringPhaseControlSync.parseFromYaml(yamlText);

        ChaosExperiment experiment = ChaosDsl.fromYaml(yamlText);
        SpringPhaseControlSync.PhaseSyncHandle phaseSyncHandle = springControlConfig == null
                ? null
                : SpringPhaseControlSync.start(springControlConfig, experiment);

        ExperimentReport report = experiment.run();
        SpringPhaseControlSync.PhaseSyncResult springPhaseSyncResult = phaseSyncHandle == null
                ? SpringPhaseControlSync.emptyResult()
                : phaseSyncHandle.finish();

        if (springControlConfig != null && springControlConfig.failOnError() && !springPhaseSyncResult.errors().isEmpty()) {
            InvariantResult springSyncInvariant = new InvariantResult(
                    "spring_control_sync",
                    false,
                    "errors=" + springPhaseSyncResult.errors()
            );
            report = mergeAdditionalInvariants(report, List.of(springSyncInvariant), 1);
        }

        List<PrometheusObservability.PrometheusCheckResult> prometheusChecks = prometheusConfig == null
                ? List.of()
                : PrometheusObservability.evaluate(prometheusConfig);
        report = mergePrometheusChecks(report, prometheusChecks);
        GrafanaObservability.GrafanaPublishResult grafanaResult = grafanaConfig == null
                ? new GrafanaObservability.GrafanaPublishResult(0, List.of(), List.of())
                : GrafanaObservability.publish(grafanaConfig, experiment, report);

        if (grafanaConfig != null && grafanaConfig.failOnError() && !grafanaResult.errors().isEmpty()) {
            InvariantResult grafanaInvariant = new InvariantResult(
                    "grafana.annotations",
                    false,
                    "errors=" + grafanaResult.errors()
            );
            report = mergeAdditionalInvariants(report, List.of(grafanaInvariant), 1);
        }

        Path reportPath = resolveReportPath(options);
        ExperimentReportJson.writeJson(report, reportPath);

        Path snapshotPath = null;
        Path metadataPath = null;
        if (options.artifactsDir != null) {
            snapshotPath = options.artifactsDir.resolve("config-snapshot.yaml");
            metadataPath = options.artifactsDir.resolve("run-metadata.json");
            RunArtifactsWriter.writeSnapshot(resolvedDslPath, snapshotPath);
            RunArtifactsWriter.writeMetadata(
                    metadataPath,
                    resolvedDslPath,
                    reportPath,
                    dslSha256,
                    options.enforceGate,
                    experiment,
                    report,
                    prometheusChecks,
                    grafanaResult,
                    springPhaseSyncResult
            );
        }

        printSummary(report, reportPath, snapshotPath, metadataPath, prometheusChecks, grafanaResult, springPhaseSyncResult);

        if (options.enforceGate) {
            try {
                ChaosCiGate.assertPassed(report);
                return 0;
            } catch (IllegalStateException ignored) {
                return 1;
            }
        }
        return ChaosCiGate.exitCode(report);
    }

    private static Path resolveReportPath(RunOptions options) {
        if (options.reportPath != null) {
            return options.reportPath;
        }
        if (options.artifactsDir != null) {
            return options.artifactsDir.resolve("report.json");
        }
        return Path.of("build", "reports", "chaos-report.json");
    }

    private static void printSummary(
            ExperimentReport report,
            Path reportPath,
            Path snapshotPath,
            Path metadataPath,
            List<PrometheusObservability.PrometheusCheckResult> prometheusChecks,
            GrafanaObservability.GrafanaPublishResult grafanaResult,
            SpringPhaseControlSync.PhaseSyncResult springPhaseSyncResult
    ) {
        StringBuilder summary = new StringBuilder();
        summary.append("Chaos run finished: ")
                .append("status=").append(report.getStatus())
                .append(", resilienceScore=").append(report.getResilienceScore())
                .append(", report=").append(reportPath);
        if (snapshotPath != null) {
            summary.append(", snapshot=").append(snapshotPath);
        }
        if (metadataPath != null) {
            summary.append(", metadata=").append(metadataPath);
        }
        if (!prometheusChecks.isEmpty()) {
            long failedPrometheusChecks = prometheusChecks.stream().filter(result -> !result.passed()).count();
            summary.append(", prometheusChecks=").append(prometheusChecks.size());
            summary.append(", failedPrometheusChecks=").append(failedPrometheusChecks);
        }
        if (grafanaResult.requested() > 0) {
            summary.append(", grafanaRequested=").append(grafanaResult.requested());
            summary.append(", grafanaCreated=").append(grafanaResult.annotationIds().size());
            summary.append(", grafanaErrors=").append(grafanaResult.errors().size());
        }
        if (springPhaseSyncResult.requestedSwitches() > 0) {
            summary.append(", springPhaseSwitches=").append(springPhaseSyncResult.requestedSwitches());
            summary.append(", springPhaseSwitchesOk=").append(springPhaseSyncResult.successfulSwitches());
            summary.append(", springPhaseErrors=").append(springPhaseSyncResult.errors().size());
        }
        if (!report.isPassed()) {
            List<String> failedInvariants = report.getInvariantResults().stream()
                    .filter(result -> !result.isPassed())
                    .map(InvariantResult::getName)
                    .collect(Collectors.toList());
            summary.append(", failedInvariants=").append(failedInvariants);
            summary.append(", executionErrors=").append(report.getExecutionErrors().size());
        }
        System.out.println(summary);
    }

    private static ExperimentReport mergePrometheusChecks(
            ExperimentReport original,
            List<PrometheusObservability.PrometheusCheckResult> prometheusChecks
    ) {
        if (prometheusChecks.isEmpty()) {
            return original;
        }

        List<InvariantResult> prometheusInvariantResults = new ArrayList<>();
        int failedPrometheusChecks = 0;
        for (PrometheusObservability.PrometheusCheckResult prometheusCheck : prometheusChecks) {
            InvariantResult invariantResult = PrometheusObservability.toInvariantResult(prometheusCheck);
            prometheusInvariantResults.add(invariantResult);
            if (!invariantResult.isPassed()) {
                failedPrometheusChecks++;
            }
        }
        return mergeAdditionalInvariants(original, prometheusInvariantResults, failedPrometheusChecks);
    }

    private static ExperimentReport mergeAdditionalInvariants(
            ExperimentReport original,
            List<InvariantResult> additionalInvariantResults,
            int failedAdditionalInvariantCount
    ) {
        if (additionalInvariantResults.isEmpty()) {
            return original;
        }

        List<InvariantResult> mergedInvariantResults = new ArrayList<>(original.getInvariantResults());
        mergedInvariantResults.addAll(additionalInvariantResults);

        boolean allInvariantsPassed = mergedInvariantResults.stream().allMatch(InvariantResult::isPassed);
        boolean noExecutionErrors = original.getExecutionErrors().isEmpty();
        ExperimentStatus status = allInvariantsPassed && noExecutionErrors ? ExperimentStatus.PASS : ExperimentStatus.FAIL;

        double adjustedScore = original.getResilienceScore();
        if (failedAdditionalInvariantCount > 0) {
            adjustedScore = clampAndRound(adjustedScore - failedAdditionalInvariantCount * 15.0);
        }

        return new ExperimentReport(
                original.getExperimentName(),
                original.getStartedAt(),
                original.getFinishedAt(),
                status,
                original.getMetrics(),
                adjustedScore,
                original.getPhaseReports(),
                mergedInvariantResults,
                original.getExecutionErrors()
        );
    }

    private static double clampAndRound(double value) {
        double clamped = Math.max(0.0, Math.min(100.0, value));
        return BigDecimal.valueOf(clamped)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static final class CliOptions {
        private final String command;
        private final RunOptions runOptions;

        private CliOptions(String command, RunOptions runOptions) {
            this.command = Objects.requireNonNull(command, "command must not be null");
            this.runOptions = runOptions;
        }

        private static CliOptions parse(String[] args) {
            if (args == null || args.length < 2) {
                throw new IllegalArgumentException(usage());
            }

            String command = args[0].trim().toLowerCase();
            if (!"run".equals(command)) {
                throw new IllegalArgumentException("Unknown command: " + command + ". " + usage());
            }
            return new CliOptions(command, RunOptions.parse(args));
        }
    }

    private static final class RunOptions {
        private final Path dslPath;
        private final Path reportPath;
        private final Path artifactsDir;
        private final boolean enforceGate;

        private RunOptions(Path dslPath, Path reportPath, Path artifactsDir, boolean enforceGate) {
            this.dslPath = Objects.requireNonNull(dslPath, "dslPath must not be null");
            this.reportPath = reportPath;
            this.artifactsDir = artifactsDir;
            this.enforceGate = enforceGate;
        }

        private static RunOptions parse(String[] args) {
            Path dslPath = Path.of(args[1]);
            Path reportPath = null;
            Path artifactsDir = null;
            boolean enforceGate = true;

            int i = 2;
            while (i < args.length) {
                String arg = args[i];
                switch (arg) {
                    case "--report" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("Missing value for --report. " + usage());
                        }
                        reportPath = Path.of(args[i + 1]);
                        i += 2;
                    }
                    case "--artifacts-dir" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("Missing value for --artifacts-dir. " + usage());
                        }
                        artifactsDir = Path.of(args[i + 1]);
                        i += 2;
                    }
                    case "--no-gate" -> {
                        enforceGate = false;
                        i++;
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg + ". " + usage());
                }
            }

            return new RunOptions(dslPath, reportPath, artifactsDir, enforceGate);
        }
    }

    private static String usage() {
        return "Usage: chaoslib run <path-to-experiment.yaml> [--report <path>] [--artifacts-dir <path>] [--no-gate]";
    }
}
