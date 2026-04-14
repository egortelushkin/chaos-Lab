import com.chaosLab.ChaosCiGate;
import com.chaosLab.ChaosExperiment;
import com.chaosLab.ExperimentReport;
import com.chaosLab.PhaseType;
import com.chaosLab.dsl.ChaosDsl;
import com.chaosLab.dsl.ChaosDslRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
