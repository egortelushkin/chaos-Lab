import com.helloegor03.ChaosEngine;
import com.helloegor03.LatencyChaosInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = ChaosStarterTest.TestConfig.class)
class ChaosStarterTest {

    @Configuration
    @Import({ChaosAutoConfiguration.class})
    static class TestConfig {}

    @Autowired
    private ChaosEngine chaosEngine;

    @Autowired
    private LatencyChaosInterceptor interceptor;

    @Test
    void beansAreLoaded() {
        assertNotNull(chaosEngine);
        assertNotNull(interceptor);
    }
}