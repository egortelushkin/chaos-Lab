package com.helloegor03;


import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ChaosAspect {

    @Around("@annotation(chaosify)")
    public Object applyChaos(ProceedingJoinPoint joinPoint, Chaosify chaosify) throws Throwable {

        var scenario = ChaosScenarios.get(chaosify.scenario());
        if (scenario != null) {
            System.out.println("Applying chaos scenario: " + chaosify.scenario());
            scenario.unleash();
        }

        return joinPoint.proceed();
    }
}