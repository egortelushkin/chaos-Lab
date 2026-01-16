

import com.chaosLab.ChaosDemoApplication;
import com.chaosLab.DemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = ChaosDemoApplication.class)
class ChaosIntegrationTest {

    @Autowired
    DemoService demoService;

    @Test
    void chaos_should_sometimes_delay() {
        boolean delayed = false;

        for (int i = 0; i < 50; i++) {
            long start = System.currentTimeMillis();
            try {
                demoService.unstableOperation();
            } catch (Exception ignored) {}

            long duration = System.currentTimeMillis() - start;

            if (duration > 50) { // НЕ 500!
                delayed = true;
                break;
            }
        }

        assertTrue(delayed);
    }
}
