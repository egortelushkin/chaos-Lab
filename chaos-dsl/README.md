# Chaos DSL

`chaos-dsl` lets you run resilience experiments from YAML without writing Java code.

## What it supports

- Experiment config: name, seed
- Load config: virtual users, worker threads, think time
- Phases: `WARMUP`, `FAULT`, `RECOVERY`
- Faults: delay and exception probabilities
- Fault targeting by operation: `fault.targetOperations`
- Invariants: max error rate, max p95 latency
- Business invariant: no duplicate order ids
- Synthetic users as ordered steps with latency and success rate
- HTTP runtime steps (real endpoint calls) with response capture and session templates

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

## CLI usage (`chaoslib run`)

```bash
./gradlew :chaos-dsl:run --args="run docs/experiment-example.yaml --artifacts-dir build/reports/chaos/run1"
```

Arguments:

- `<path-to-experiment.yaml>`: required
- `--artifacts-dir <path>`: optional directory for `report.json`, `config-snapshot.yaml`, `run-metadata.json`
- `--report <path>`: optional JSON report output
- `--no-gate`: optional, do not throw on failed experiment

Without `--no-gate`, CLI enforces CI gate and returns non-zero on failure.

Legacy direct runner still works:

```bash
./gradlew :chaos-dsl:classes
java -cp chaos-dsl/build/classes/java/main;chaos-core/build/classes/java/main com.chaosLab.dsl.ChaosDslRunner docs/experiment-example.yaml --report build/reports/chaos-report.json --no-gate
```

## Targeted fault example

```yaml
fault:
  exceptionProbability: 0.15
  targetOperations:
    - checkout
```

With this config, chaos is injected only for steps whose operation is `checkout`.

## Duplicate order ID guard

```yaml
invariants:
  noDuplicateOrderIds: true

users:
  steps:
    - operation: checkout
      successRate: 1.0
      emitOrderId: true
      duplicateOrderIdRate: 0.0
```

- `emitOrderId: true` makes DSL step emit order IDs for successful operations.
- `duplicateOrderIdRate` controls how often a previous order ID is reused in that step (`0.0..1.0`).

## Spring Boot runtime flow example

This mode executes real HTTP requests against your running service (for example with `@Chaosify` in Spring layer).

```yaml
experiment:
  name: checkout_runtime
load:
  virtualUsers: 5
  workerThreads: 5
phases:
  - name: fault
    type: FAULT
    durationMs: 120000
invariants:
  maxErrorRate: 0.03
  noDuplicateOrderIds: true
users:
  steps:
    - operation: create_order
      method: POST
      url: http://localhost:8080/orders?price=100
      successStatusCodes: [200, 201]
      capture:
        orderId: order.id
    - operation: pay_order
      method: POST
      url: http://localhost:8080/orders/${orderId}/pay
      successStatusCodes: [200]
      emitOrderIdJsonField: result.order.id
```

Supported HTTP step fields:
- `method`, `url` (required together)
- `body`, `headers`, `timeoutMs`
- `successStatusCodes` (default: any `2xx`)
- `capture`: map `sessionKey -> jsonPath` from response body
- `emitOrderIdFromSession` or `emitOrderIdJsonField` for `noDuplicateOrderIds`
- JSON path supports `field.subfield`, array indexes (`items[0].id`), or JSON Pointer (`/field/subfield`)

## Prometheus/Grafana integration

You can add Prometheus checks directly in DSL.  
If Grafana is used with Prometheus datasource, the same metrics/queries will match dashboards.

```yaml
observability:
  prometheus:
    baseUrl: http://localhost:9090
    timeoutMs: 5000
    checks:
      - name: error_rate_prom
        query: demo_error_rate
        mode: instant
        operator: <=
        threshold: 0.03
      - name: p95_latency_prom
        query: demo_p95_latency_ms
        mode: range
        rangeSeconds: 300
        stepSeconds: 15
        reducer: max
        operator: <=
        threshold: 800
```

Supported operators: `<`, `<=`, `>`, `>=`, `==`, `!=`.
Supported `mode`: `instant` (`/api/v1/query`) and `range` (`/api/v1/query_range`).
Supported range `reducer`: `max`, `min`, `avg`, `last`.

Prometheus checks are:
- evaluated after experiment run,
- added to `invariantResults` in `report.json`,
- included in `run-metadata.json`,
- enforced by gate (non-zero exit code on failure).

Grafana annotations:

```yaml
observability:
  grafana:
    baseUrl: http://localhost:3000
    apiTokenEnv: GRAFANA_API_TOKEN
    dashboardUid: checkout-dashboard
    panelId: 12
    tags: [chaoslib, checkout]
    annotatePhases: true
    annotateRun: true
    failOnError: false
```

Grafana integration behavior:
- posts annotations to `/api/annotations` for each phase and/or whole run,
- writes annotation stats to `run-metadata.json`,
- can fail gate on annotation delivery errors when `failOnError: true`.
