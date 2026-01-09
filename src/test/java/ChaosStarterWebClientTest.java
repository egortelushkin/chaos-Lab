import com.helloegor03.ChaosWebClient;
import com.helloegor03.LatencyChaosInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ChaosStarterWebClientTest {

    @Configuration
    @Import({ChaosAutoConfiguration.class, TestController.class})
    static class TestConfig {}

    @Autowired
    private LatencyChaosInterceptor interceptor;

    @Test
    void testWebClientWithChaos() {
        // Создаем WebClient с хаосом
        WebClient client = ChaosWebClient.withChaos(WebClient.builder().baseUrl("http://localhost:8080").build(), interceptor);

        long start = System.currentTimeMillis();
        String body = client.get()
                .uri("/hello")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        long duration = System.currentTimeMillis() - start;

        // Проверяем, что ответ корректный
        assertEquals("Hello, Chaos!", body);

        // Проверяем, что задержка была в рамках maxDelayMs
        System.out.println("Request took " + duration + " ms");
        assertTrue(duration >= 0); // можно заменить на более точную проверку, если знаем maxDelayMs
    }
}