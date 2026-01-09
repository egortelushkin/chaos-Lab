import com.helloegor03.Chaos;
import com.helloegor03.ChaosBuilder;
import com.helloegor03.ChaosEngine;
import com.helloegor03.LatencyChaosInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChaosProperties.class)
@ConditionalOnProperty(prefix = "chaos", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChaosAutoConfiguration {

    @Bean
    public ChaosEngine chaosEngine(ChaosProperties properties) {
        ChaosBuilder builder = Chaos.builder();

        // HTTP chaos
        if (properties.getHttp().getMaxDelayMs() > 0) {
            builder.delay((int) properties.getHttp().getMaxDelayMs())
                    .probability(properties.getHttp().getDelayProbability());
        }

        // TODO: add Kafka chaos and others
        return builder.build();
    }

    @Bean
    public LatencyChaosInterceptor latencyChaosInterceptor(ChaosEngine engine, ChaosProperties properties) {
        return new LatencyChaosInterceptor(properties.getHttp().getMaxDelayMs());
    }
}