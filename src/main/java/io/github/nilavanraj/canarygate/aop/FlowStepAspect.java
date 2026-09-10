package io.github.nilavanraj.canarygate.aop;

import io.github.nilavanraj.canarygate.CanaryGateService;
import io.github.nilavanraj.canarygate.exception.GateBlockedException;
import io.github.nilavanraj.canarygate.model.StepOutcome;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@RequiredArgsConstructor
public class FlowStepAspect {

    private final CanaryGateService service;

    @Around("@annotation(flowStep)")
    public Object around(ProceedingJoinPoint pjp, FlowStep flowStep) throws Throwable {
        String stepName = flowStep.value();
        try {
            Object result = pjp.proceed();
            service.record(stepName, StepOutcome.SUCCESS);
            return result;
        } catch (GateBlockedException e) {
            throw e;
        } catch (Throwable t) {
            service.record(stepName, StepOutcome.FAILURE);
            throw t;
        }
    }
}
