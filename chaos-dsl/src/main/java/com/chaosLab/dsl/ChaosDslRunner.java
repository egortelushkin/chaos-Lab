package com.chaosLab.dsl;

import com.chaosLab.ChaosCiGate;
import com.chaosLab.ChaosExperiment;
import com.chaosLab.ExperimentReport;
import com.chaosLab.ExperimentReportJson;

import java.nio.file.Path;
import java.util.Objects;

public final class ChaosDslRunner {

    private ChaosDslRunner() {
    }

    public static ExperimentReport run(Path dslPath) {
        ChaosExperiment experiment = ChaosDsl.fromFile(dslPath);
        return experiment.run();
    }

    public static ExperimentReport run(Path dslPath, Path reportPath, boolean enforceGate) {
        ExperimentReport report = run(dslPath);
        if (reportPath != null) {
            ExperimentReportJson.writeJson(report, reportPath);
        }
        if (enforceGate) {
            ChaosCiGate.assertPassed(report);
        }
        return report;
    }

    public static ExperimentReport runWithCiGate(Path dslPath) {
        return run(dslPath, null, true);
    }

    public static void main(String[] args) {
        CliOptions options = CliOptions.parse(args);
        ExperimentReport report = run(options.dslPath, options.reportPath, options.enforceGate);

        if (options.enforceGate) {
            System.exit(0);
        }
        System.exit(ChaosCiGate.exitCode(report));
    }

    private static final class CliOptions {
        private final Path dslPath;
        private final Path reportPath;
        private final boolean enforceGate;

        private CliOptions(Path dslPath, Path reportPath, boolean enforceGate) {
            this.dslPath = Objects.requireNonNull(dslPath, "dslPath must not be null");
            this.reportPath = reportPath;
            this.enforceGate = enforceGate;
        }

        private static CliOptions parse(String[] args) {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException(usage());
            }

            Path dslPath = Path.of(args[0]);
            Path reportPath = null;
            boolean enforceGate = true;

            int i = 1;
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
                    case "--no-gate" -> {
                        enforceGate = false;
                        i++;
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg + ". " + usage());
                }
            }

            return new CliOptions(dslPath, reportPath, enforceGate);
        }

        private static String usage() {
            return "Usage: ChaosDslRunner <path-to-experiment.yaml> [--report <path>] [--no-gate]";
        }
    }
}
