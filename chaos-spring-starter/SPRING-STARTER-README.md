# Chaos Spring Starter

Chaos Spring Starter is a lightweight Spring Boot library that allows you to inject controlled chaos into your microservices for testing resilience and fault tolerance. With this starter, you can simulate delays, exceptions, CPU spikes, memory leaks, and other failure scenarios declaratively through a YAML configuration without touching your business logic.

---

## Features

- Declarative chaos scenarios via `application.yaml` or `application.properties`.
- Inject chaos into any Spring-managed method using the `@Chaosify` annotation.
- Supports multiple types of effects:
    - **Delay** (simulate network or processing latency)
    - **Exception** (simulate errors)
    - **CPU spike** (simulate high CPU usage)
    - **Memory leak** (simulate memory pressure)
- Multiple scenarios with independent configurations.
- Works asynchronously, so effects won't block your main application flow.
- Fully compatible with Spring Boot 3+ and Java 17+.

---

## Getting Started

### 1. Add Dependency

Add your starter to your `build.gradle`:

```gradle
implementation project(":chaos-spring-starter")
```

Or if published to Maven:

```
<dependency>
    <groupId>com.chaosLab</groupId>
    <artifactId>chaos-spring-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure Chaos Scenarios
Add chaos scenarios in your application.yaml:
```
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
probability: 0.5
max-ms: 1000
exception:
probability: 0.2
cpu-spike:
duration-ms: 100
memory-leak:
bytes: 5242880
```

- enabled: enable or disable the scenario.

- delay.probability and delay.max-ms: chance and max duration of artificial delay.

- exception.probability: chance of throwing a runtime exception.

- cpu-spike.duration-ms: simulate CPU load for a number of milliseconds.

- memory-leak.bytes: allocate memory to simulate memory pressure.

### 3. Annotate Methods with @Chaosify
Annotate any Spring-managed method where you want to inject chaos:

```java
import com.chaosLab.chaosspringstarter.annotation.Chaosify;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Chaosify(scenario = "default")
    public String getUserData(int userId) {
        return "User-" + userId;
    }

    @Chaosify(scenario = "stress")
    public String processRequest(String request) {
        return "Processed: " + request;
    }
}
```

### 4. How it Works
When a method annotated with `@Chaosify` is called, the Chaos Spring Starter checks the configured scenario. Based on the defined probabilities, it may introduce delays, throw exceptions, or simulate CPU/memory stress before proceeding with the original method execution.

Default `@Chaosify` scenario name is `default`.
Legacy aliases are still supported:
- `DefaultChaosScenario` -> `default`
- `StressChaos` -> `stress`

### 5. Example Usage
```java
@SpringBootTest
class UserServiceChaosTest {

    @Autowired
    private UserService userService;

    @Test
    void testChaosEffects() {
        for (int i = 0; i < 10; i++) {
            try {
                System.out.println(userService.getUserData(i));
                System.out.println(userService.processRequest("req-" + i));
            } catch (RuntimeException e) {
                System.out.println("Chaos exception caught: " + e.getMessage());
            }
        }
    }
}
```

### 6. Benefits
- Test the resilience of your microservices under various failure scenarios.
- Easily toggle chaos scenarios on/off with YAML or Spring Profiles.
- Fully extendable with custom ChaosEffects.

### 7. Extending with Custom Effects
Implement the ChaosEffect interface for your own effects:
```java
public class DiskIOLoadEffect implements ChaosEffect {
    @Override
    public void apply() {
        // simulate disk IO load
    }
}
```
Then add it to a scenario programmatically:
```java
scenario.getEngine().addRule(new ChaosRule(0.5, new DiskIOLoadEffect()));
```

### 8. Notes
- Spring Boot 3+ and Java 17+ are required.
- Use @Chaosify only on Spring-managed beans (services, controllers, etc.).
- Asynchronous execution ensures that chaos effects do not block the main thread.

### 9. Missing Scenario Policy
By default, missing scenario names cause an exception in aspect:

```yaml
chaos:
  fail-on-missing-scenario: true
```

Set `false` to only log a warning and continue method execution.

### 10. Scenario Control API (Optional)
Enable runtime control endpoints:

```yaml
chaos:
  control:
    enabled: true
```

Endpoints:
- `GET /chaos/control/scenarios`
- `POST /chaos/control/scenarios/{name}/enable`
- `POST /chaos/control/scenarios/{name}/disable`
- `POST /chaos/control/scenarios/disable-all`
- `POST /chaos/control/scenarios/enable-only` with body `{"names":["default","stress"]}`

### Enjoy chaos! 💥
