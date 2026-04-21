# Chaos Demo

This module now includes a visual end-to-end showcase of ChaosLib on a real Spring Boot app.

## What it demonstrates

- Real HTTP business flow (`create order -> pay order`)
- Phase timeline (`baseline -> fault -> recovery`)
- Runtime scenario switching through Spring control API
- Final gate result (`PASS/FAIL`), resilience score, phase metrics, and artifact files

## Run

```bash
./gradlew :chaos-demo:bootRun
```

In another terminal:

```bash
curl -X POST "http://localhost:8080/demo/showcase/run?mode=quick"
```

Use `mode=full` for a longer run.

## Endpoints

- `GET /demo/showcase` -> usage info
- `POST /demo/showcase/run?mode=quick|full` -> run showcase now

Response includes:

- overall status and resilience score
- global metrics (`errorRate`, `p95LatencyMs`, operations)
- per-phase metrics
- failed invariants (if any)
- report artifact paths (`report.json`, `run-metadata.json`, DSL snapshot)
