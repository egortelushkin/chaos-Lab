<!-- ========================= -->
<!--        CHAOS LAB          -->
<!-- ========================= -->

<p align="center">
  <img src="docs/logo (2).png" alt="ChaosLab Logo" width="500"/>
</p>


<p align="center">
  Lightweight Chaos Engineering library for Spring Boot
</p>

<p align="center">
  <a href="#english">English</a> | <a href="#russian">Русский</a>
</p>

---

## ⚠️ Project Status

> **Version:** `0.2.0`  
> **Status:** Experimental / Test Release  
>  
> This project is under active development.  
> APIs may change. Not recommended for production use.

---

<a name="english"></a>

# 📘 English Documentation

## What is ChaosLab?

**ChaosLab** is a modular Java library for **Chaos Engineering** in Spring Boot applications.

It allows you to **inject controlled failures** (delays, exceptions, CPU spikes, memory leaks) directly into your business logic using annotations and configuration.

Inspired by Netflix Chaos Monkey, but designed for:
- local development
- test & staging environments
- learning and experimentation

---

## ✨ Features

- 🎯 Named chaos scenarios
- 🧩 Modular architecture (core / spring / starter)
- 🪄 `@Chaosify` annotation
- ⚙️ YAML-based configuration
- 🔀 Probabilistic chaos effects
- 🧪 Integration-test friendly
- 🧠 Easily extensible effects system

---

## 📦 Modules Overview

### `chaos-core`
Pure Java module with no Spring dependencies.

Includes:
- `ChaosEngine`
- `ChaosScenario`
- `ChaosRule`
- `ChaosEffect`
- Fluent DSL builder

> Can be used independently of Spring.

---

### `chaos-spring`
Spring integration module:
- Aspect-Oriented Programming (AOP)
- `@Chaosify` annotation
- Method interception

---

### `chaos-spring-starter`
Spring Boot Starter:
- Auto-configuration
- `@ConfigurationProperties`
- Scenario registration via `application.yml`

---

## 🚀 Quick Start

### 1️⃣ Add dependency

```gradle
implementation "com.chaosLab:chaos-spring-starter:0.1.0"
```

### 2️⃣ Configure chaos scenarios
```yaml
chaos:
  scenarios:
    default:
      enabled: true
      delay:
        probability: 0.3
        max-ms: 500
      exception:
        probability: 0.1

    stress:
      enabled: true
      delay:
        probability: 0.7
        max-ms: 1500
      exception:
        probability: 0.3
      cpu-spike:
        duration-ms: 200
      memory-leak:
        bytes: 5242880
```

### 3️⃣ Annotate your code
```java
@Service
public class PaymentService {

    @Chaosify(scenario = "stress")
    public void processPayment() {
        // business logic
    }
}
```
Chaos will be injected automatically on method execution.

### 🌐 REST Demo Example
```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }
```

```java
    @PostMapping
    public Order create(@RequestParam double price) {
        return service.create(price);
    }

    @PostMapping("/{id}/pay")
    public Order pay(@PathVariable UUID id) {
        return service.pay(id);
    }
}
```
```java
@Service
public class OrderService {

    @Chaosify(scenario = "default")
    public Order create(double price) {
        return new Order(price);
    }

    @Chaosify(scenario = "stress")
    public Order pay(UUID id) {
        return markAsPaid(id);
    }
}
```
### 💥 Supported Chaos Effects
| Effect                   | Description            |
| ------------------------ | ---------------------- |
| `DelayEffect`            | Random execution delay |
| `ExceptionEffect`        | Random exception       |
| `CpuSpikeEffect`         | Temporary CPU load     |
| `MemoryLeakEffect`       | Simulated memory leak  |
| `PartialExceptionEffect` | Probabilistic failures |

### 🧠 Architecture Overview
```css
@Chaosify
   ↓
ChaosAspect
   ↓
ChaosScenario
   ↓
ChaosEngine
   ↓
ChaosRule → ChaosEffect
```

### Roadmap / In Progress

The following modules are currently under development:

🔹 Kafka Chaos Module
- producer delays
- consumer failures
- message loss
- duplicate delivery

🔹 HTTP Chaos Module
- RestTemplate / WebClient
- artificial timeouts
- 4xx / 5xx injection
- downstream degradation

You are welcome to actively participate in the development of these modules.

### 🤝 Contributing
Contributions are very welcome:
- new chaos effects
- bug fixes
- performance improvements
- documentation
- examples & demos
If you're interested in Chaos Engineering — join the project 🚀

### 📌 Versioning
```arduino 
0.1.0 — experimental test release
```
