import com.chaosLab.Chaos;
import com.chaosLab.ChaosCiGate;
import com.chaosLab.ChaosEngine;
import com.chaosLab.ChaosExperiment;
import com.chaosLab.ExperimentRegression;
import com.chaosLab.ExperimentRegressionAnalyzer;
import com.chaosLab.ChaosRunResult;
import com.chaosLab.ChaosScenario;
import com.chaosLab.ChaosScenarios;
import com.chaosLab.ExperimentReport;
import com.chaosLab.ExperimentReportJson;
import com.chaosLab.PhaseType;
import com.chaosLab.RegressionThresholds;
import com.chaosLab.StepResult;
import com.chaosLab.StepSequenceUser;
import com.chaosLab.UserStep;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosTest {

    @Test
    void testExceptionEffect() {
        ChaosEngine engine = Chaos.builder()
                .exception().probability(1.0)
                .build();
        assertThrows(RuntimeException.class, engine::unleash);
    }

    @Test
    void testDelayEffect() {
        ChaosEngine engine = Chaos.builder()
                .delay(100).probability(1.0)
                .build();
        long start = System.currentTimeMillis();
        engine.unleash();
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration >= 0 && duration <= 300);
    }

    @Test
    void testDynamicProbability() {
        ChaosEngine engine = Chaos.builder()
                .exception().dynamicProbability(() -> 0.0)
                .build();
        assertDoesNotThrow(engine::unleash);
    }

    @Test
    void testScenarioEnableDisable() {
        ChaosScenario scenario = Chaos.builder()
                .exception().probability(1.0)
                .scenario("test");
        scenario.disable();
        assertDoesNotThrow(scenario::unleash);
        scenario.enable();
        assertThrows(RuntimeException.class, scenario::unleash);
    }

    @Test
    void testInvalidProbabilityFailsFast() {
        ChaosEngine engine = Chaos.builder()
                .delay(10).probability(1.5)
                .build();
        assertThrows(IllegalStateException.class, engine::run);
    }

    @Test
    void testZeroDelayDoesNotThrow() {
        ChaosEngine engine = Chaos.builder()
                .delay(0).probability(1.0)
                .build();
        assertDoesNotThrow(engine::unleash);
    }

    @Test
    void testSeedMakesDecisionsDeterministic() {
        ChaosEngine engine1 = Chaos.builder()
                .withSeed(42L)
                .delay(1).probability(0.5)
                .delay(1).probability(0.5)
                .delay(1).probability(0.5)
                .delay(1).probability(0.5)
                .delay(1).probability(0.5)
                .build();

        ChaosEngine engine2 = Chaos.builder()
                .withSeed(42L)
                .delay(1).probability(0.5)
                .delay(1).probability(0.5)
                .delay(1).probability(0.5)
                .delay(1).probability(0.5)
                .delay(1).probability(0.5)
                .build();

        ChaosRunResult result1 = engine1.run();
        ChaosRunResult result2 = engine2.run();
        assertEquals(result1.getAppliedRules(), result2.getAppliedRules());
        assertEquals(result1.getSkippedRules(), result2.getSkippedRules());
    }

    @Test
    void testDisabledScenarioRunReturnsSkippedResult() {
        ChaosScenario scenario = Chaos.builder()
                .delay(10).probability(1.0)
                .scenario("disabled");

        scenario.disable();
        ChaosRunResult result = scenario.run();

        assertEquals(1, result.getTotalRules());
        assertEquals(0, result.getAppliedRules());
        assertEquals(1, result.getSkippedRules());
        assertTrue(result.isSuccess());
    }

    @Test
    void testExperimentPassesWhenInvariantsAreMet() {
        ChaosExperiment experiment = Chaos.experiment("happy-path")
                .virtualUsers(2)
                .workerThreads(2)
                .users(() -> session -> StepResult.success("ok"))
                .warmup(Duration.ofMillis(80))
                .fault(Duration.ofMillis(80))
                .recovery(Duration.ofMillis(80))
                .maxErrorRate(0.05)
                .maxP95LatencyMs(50)
                .build();

        ExperimentReport report = experiment.run();
        assertTrue(report.isPassed());
        assertTrue(report.getMetrics().getTotalOperations() > 0);
        assertTrue(report.getResilienceScore() >= 0.0 && report.getResilienceScore() <= 100.0);
        assertEquals(3, report.getPhaseReports().size());
        assertEquals(PhaseType.WARMUP, report.getPhaseReports().get(0).getPhaseType());
        assertEquals(PhaseType.FAULT, report.getPhaseReports().get(1).getPhaseType());
        assertEquals(PhaseType.RECOVERY, report.getPhaseReports().get(2).getPhaseType());
    }

    @Test
    void testExperimentFailsOnErrorRateInvariant() {
        ChaosExperiment experiment = Chaos.experiment("error-rate-fail")
                .virtualUsers(1)
                .workerThreads(1)
                .users(() -> session -> StepResult.failure("always-fail"))
                .warmup(Duration.ofMillis(120))
                .fault(Duration.ofMillis(120))
                .maxErrorRate(0.10)
                .build();

        ExperimentReport report = experiment.run();
        assertFalse(report.isPassed());
        assertTrue(report.getMetrics().getErrorRate() > 0.10);
        assertTrue(report.getResilienceScore() < 100.0);
    }

    @Test
    void testExperimentFailsOnP95Invariant() {
        ChaosExperiment experiment = Chaos.experiment("p95-fail")
                .virtualUsers(1)
                .workerThreads(1)
                .users(() -> session -> {
                    Thread.sleep(30);
                    return StepResult.success("slow-step");
                })
                .fault(Duration.ofMillis(180))
                .maxP95LatencyMs(10)
                .build();

        ExperimentReport report = experiment.run();
        assertFalse(report.isPassed());
        assertTrue(report.getMetrics().getP95LatencyMs() > 10);
    }

    @Test
    void testStatefulSequenceSyntheticUser() {
        ChaosExperiment experiment = Chaos.experiment("stateful-flow")
                .virtualUsers(1)
                .workerThreads(1)
                .users(() -> new com.chaosLab.StepSequenceUser(List.of(
                        session -> {
                            session.putAttribute("stage", "login");
                            return StepResult.success("login");
                        },
                        session -> {
                            String stage = session.getAttribute("stage", String.class);
                            if (!"login".equals(stage)) {
                                return StepResult.failure("bad-state");
                            }
                            session.putAttribute("stage", "checkout");
                            return StepResult.success("checkout");
                        }
                )))
                .fault(Duration.ofMillis(120))
                .maxErrorRate(0.0)
                .build();

        ExperimentReport report = experiment.run();
        assertTrue(report.isPassed());
        assertEquals(0.0, report.getMetrics().getErrorRate());
    }

    @Test
    void testFaultPhaseUsesChaosEngine() {
        ChaosEngine faultEngine = Chaos.builder()
                .exception().probability(1.0)
                .build();

        ChaosExperiment experiment = Chaos.experiment("fault-phase")
                .virtualUsers(1)
                .workerThreads(1)
                .users(() -> session -> StepResult.success("business-call"))
                .warmup(Duration.ofMillis(60))
                .fault(Duration.ofMillis(120))
                .maxErrorRate(0.0)
                .faultEngine(faultEngine)
                .build();

        ExperimentReport report = experiment.run();
        assertFalse(report.isPassed());
        assertTrue(report.getMetrics().getFailedOperations() > 0);
        assertThrows(IllegalStateException.class, () -> ChaosCiGate.assertPassed(report));
        assertEquals(1, ChaosCiGate.exitCode(report));
    }

    @Test
    void testFaultTargetsOnlyConfiguredOperations() {
        AtomicInteger loginCalls = new AtomicInteger();
        AtomicInteger checkoutCalls = new AtomicInteger();
        AtomicInteger faultInjections = new AtomicInteger();

        ChaosEngine faultEngine = new ChaosEngine()
                .addRule(new com.chaosLab.ChaosRule(1.0, () -> faultInjections.incrementAndGet()));

        ChaosExperiment experiment = Chaos.experiment("targeted-fault")
                .virtualUsers(1)
                .workerThreads(1)
                .users(() -> new StepSequenceUser(List.of(
                        UserStep.named("login", session -> {
                            loginCalls.incrementAndGet();
                            return StepResult.success("login");
                        }),
                        UserStep.named("checkout", session -> {
                            checkoutCalls.incrementAndGet();
                            return StepResult.success("checkout");
                        })
                )))
                .fault(Duration.ofMillis(120))
                .faultEngine(faultEngine)
                .faultTargetOperations("checkout")
                .maxErrorRate(0.0)
                .build();

        ExperimentReport report = experiment.run();

        assertTrue(report.isPassed());
        assertTrue(loginCalls.get() > 0);
        assertTrue(checkoutCalls.get() > 0);
        assertEquals(checkoutCalls.get(), faultInjections.get());
        assertTrue(faultInjections.get() < report.getMetrics().getTotalOperations());
    }

    @Test
    void testNoDuplicateOrderIdsInvariantFailsOnDuplicates() {
        ChaosExperiment experiment = Chaos.experiment("duplicate-orders")
                .virtualUsers(1)
                .workerThreads(1)
                .users(() -> session -> StepResult.successWithOrderId("checkout", "order-constant"))
                .fault(Duration.ofMillis(120))
                .noDuplicateOrderIds()
                .build();

        ExperimentReport report = experiment.run();

        assertFalse(report.isPassed());
        assertTrue(report.getMetrics().getDuplicateOrderIds() > 0);
        assertTrue(report.getInvariantResults().stream()
                .anyMatch(result -> "no_duplicate_order_ids".equals(result.getName()) && !result.isPassed()));
    }

    @Test
    void testReportJsonExport() throws Exception {
        ChaosExperiment experiment = Chaos.experiment("json-export")
                .virtualUsers(1)
                .workerThreads(1)
                .users(() -> session -> StepResult.success("ok"))
                .fault(Duration.ofMillis(100))
                .maxErrorRate(0.0)
                .build();

        ExperimentReport report = experiment.run();
        String json = ExperimentReportJson.toJson(report);

        assertTrue(json.contains("\"experimentName\":\"json-export\""));
        assertTrue(json.contains("\"resilienceScore\""));
        assertTrue(json.contains("\"phaseReports\""));

        Path tempFile = Files.createTempFile("chaos-report-", ".json");
        ExperimentReportJson.writeJson(report, tempFile);
        String fileContent = Files.readString(tempFile);
        assertNotNull(fileContent);
        assertTrue(fileContent.contains("\"status\""));
    }

    @Test
    void testRegressionAnalysisAndGate() {
        ChaosExperiment baselineExperiment = Chaos.experiment("baseline")
                .virtualUsers(1)
                .workerThreads(1)
                .users(() -> session -> StepResult.success("ok"))
                .fault(Duration.ofMillis(120))
                .maxErrorRate(0.05)
                .build();

        ChaosExperiment currentExperiment = Chaos.experiment("current")
                .virtualUsers(1)
                .workerThreads(1)
                .users(() -> session -> StepResult.failure("fail"))
                .fault(Duration.ofMillis(120))
                .maxErrorRate(1.0)
                .build();

        ExperimentReport baseline = baselineExperiment.run();
        ExperimentReport current = currentExperiment.run();

        ExperimentRegression regression = ExperimentRegressionAnalyzer.compare(baseline, current);
        assertTrue(regression.getErrorRateDelta() > 0.0);
        assertTrue(regression.getResilienceScoreDelta() < 0.0);

        assertThrows(
                IllegalStateException.class,
                () -> ChaosCiGate.assertNoRegression(
                        baseline,
                        current,
                        new RegressionThresholds(0.01, 1000.0, 1.0)
                )
        );
    }

    @Test
    void testScenarioAliasesAndEnableOnlyControlApi() {
        ChaosScenarios.clear();
        try {
            ChaosScenario defaultScenario = Chaos.builder()
                    .delay(1).probability(0.0)
                    .scenario("DefaultChaosScenario");
            ChaosScenario stressScenario = Chaos.builder()
                    .delay(1).probability(0.0)
                    .scenario("stress");

            ChaosScenarios.register(defaultScenario);
            ChaosScenarios.register(stressScenario);

            assertNotNull(ChaosScenarios.get("default"));
            assertNotNull(ChaosScenarios.get("DefaultChaosScenario"));
            assertNotNull(ChaosScenarios.get("StressChaos"));
            assertNotNull(ChaosScenarios.get("stress"));

            int affected = ChaosScenarios.enableOnly(List.of("default"));
            assertEquals(2, affected);
            assertTrue(defaultScenario.isEnabled());
            assertFalse(stressScenario.isEnabled());

            assertTrue(ChaosScenarios.disable("default"));
            assertFalse(defaultScenario.isEnabled());
            assertTrue(ChaosScenarios.enable("DefaultChaosScenario"));
            assertTrue(defaultScenario.isEnabled());
        } finally {
            ChaosScenarios.clear();
        }
    }
}
