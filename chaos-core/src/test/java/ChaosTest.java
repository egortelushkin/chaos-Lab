import com.helloegor03.Chaos;
import com.helloegor03.ChaosEngine;
import com.helloegor03.ChaosScenario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        assertTrue(duration >= 0 && duration <= 200);
    }

    @Test
    void testDynamicProbability() {
        ChaosEngine engine = Chaos.builder()
                .exception().dynamicProbability(() -> 0.0)
                .build();
        assertDoesNotThrow(engine::unleash); // dynamic probability is 0, so no exception
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
}