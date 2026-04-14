# Chaos DSL

`chaos-dsl` lets you run resilience experiments from YAML without writing Java code.

## What it supports

- Experiment config: name, seed
- Load config: virtual users, worker threads, think time
- Phases: `WARMUP`, `FAULT`, `RECOVERY`
- Faults: delay and exception probabilities
- Invariants: max error rate, max p95 latency
- Synthetic users as ordered steps with latency and success rate

## Run from Java

```java
import com.chaosLab.ExperimentReport;
import com.chaosLab.dsl.ChaosDslRunner;

ExperimentReport report = ChaosDslRunner.run(
        Path.of("docs/experiment-example.yaml"),
        Path.of("build/reports/chaos-report.json"),
        false
);
```

## CLI usage

```bash
./gradlew :chaos-dsl:classes
java -cp chaos-dsl/build/classes/java/main;chaos-core/build/classes/java/main com.chaosLab.dsl.ChaosDslRunner docs/experiment-example.yaml --report build/reports/chaos-report.json --no-gate
```

Arguments:

- `<path-to-experiment.yaml>`: required
- `--report <path>`: optional JSON report output
- `--no-gate`: optional, do not throw on failed experiment

Without `--no-gate`, runner enforces CI gate and exits non-zero on failure.
