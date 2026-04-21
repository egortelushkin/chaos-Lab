package com.chaosLab;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        ChaosAspect.class,
        ChaosSpringConfig.class,
        ChaosStarterConfig.class,
        ChaosScenarioControlService.class,
        ChaosScenarioControlController.class
})
public class ChaosSpringStarterAutoConfiguration {
}
