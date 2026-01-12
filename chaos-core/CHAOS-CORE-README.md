# Chaos Core

**Chaos Core** is a lightweight Java library for introducing controlled chaos into your Spring Boot microservices and Java applications.  
It helps you simulate delays, exceptions, and other failure scenarios to test system resilience, error handling, and stability under unpredictable conditions.

---

## Features

- Inject random delays and exceptions in your code.
- Set fixed or dynamic probabilities for chaos effects.
- Organize chaos rules into named scenarios.
- Asynchronous execution of effects.
- Simple fluent API for defining chaos rules.

---

## Installation

Include the library in your Maven or Gradle project (assuming you publish it to your own repository):

```xml
<dependency>
    <groupId>com.helloegor03</groupId>
    <artifactId>chaos-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or for Gradle:
```xml
implementation 'com.helloegor03:chaos-core:1.0.0'
```

Usage
Basic Chaos Engine

```java
import com.helloegor03.*;

ChaosEngine engine = Chaos.builder()
                          .delay(500).probability(0.3)   // 30% chance of delay up to 500ms
                          .exception().probability(0.1) // 10% chance of throwing an exception
                          .build();

engine.unleash(); // Apply all chaos rules
```

Using Chaos Scenarios

```java
import com.helloegor03.*;

ChaosScenario scenario = Chaos.builder()
                              .delay(200).probability(0.5)
                              .exception().probability(0.1)
                              .scenario("ResilienceTest");

ChaosScenarios.register(scenario); // Register globally
scenario.unleash(); // Apply chaos if scenario is enabled
```

Enable or disable scenarios dynamically:

```java
scenario.enable();
scenario.disable();
```

Dynamic Probabilities

```java
import java.util.function.DoubleSupplier;

DoubleSupplier dynamicProb = () -> Math.random() < 0.5 ? 0.7 : 0.2;

Chaos.builder()
     .delay(300).dynamicProbability(dynamicProb)
     .build()
     .unleash();
```
---

## API Overview
### ChaosBuilder

Used to define chaos rules.

- delay(int maxDelayMs) – random delay up to maxDelayMs milliseconds.
 
- exception() – throw a RuntimeException.

- probability(double p) – fixed probability.

- dynamicProbability(DoubleSupplier p) – dynamic probability.

- build() – returns a ChaosEngine.

- scenario(String name) – create a named ChaosScenario.

ChaosEngine

Executes all added chaos rules asynchronously.

- addRule(ChaosRule rule) – add a single rule.

- unleash() – execute rules based on their probability.

ChaosRule

Defines a rule with a probability and effect.

- shouldApply() – checks if the rule should run.

- apply() – applies the effect.

- getEffect() – gets the associated ChaosEffect.

ChaosEffect

Interface for chaos effects.

- void apply() throws Exception;

Built-in effects:

- DelayEffect – random delay.

- ExceptionEffect – throws a runtime exception.

ChaosScenario

Groups rules under a named scenario.

- enable() / disable() – control scenario execution.

- unleash() – apply rules if enabled.

- getName() / getEngine() – retrieve scenario info.

ChaosScenarios

Global scenario registry.

- register(ChaosScenario scenario) – add scenario.

- get(String name) – retrieve scenario by name.