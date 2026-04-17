package com.chaosLab;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ChaosAspect {

    private static final Logger log = LoggerFactory.getLogger(ChaosAspect.class);

    @Value("${chaos.fail-on-missing-scenario:true}")
    private boolean failOnMissingScenario;

    @Around("@annotation(chaosify)")
    public Object applyChaos(ProceedingJoinPoint joinPoint, Chaosify chaosify) throws Throwable {
        String scenarioName = chaosify.scenario();
        log.debug("CHAOS triggered: scenario={}, method={}", scenarioName, joinPoint.getSignature().toShortString());

        ChaosScenario scenario = ChaosScenarios.get(scenarioName);
        if (scenario == null) {
            String message = "Chaos scenario not found: '" + scenarioName + "'. Registered scenarios: " + ChaosScenarios.all().keySet();
            if (failOnMissingScenario) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        } else {
            scenario.unleash();
        }

        return joinPoint.proceed();
    }
}
