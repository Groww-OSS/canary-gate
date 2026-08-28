package io.github.nilavanraj.canarygate.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the entry point of a guarded flow.
 *
 * Before the method body runs, the aspect calls {@code isAllowed} for the named gate.
 * If the gate is OPEN and the session is not a guinea pig, {@link io.github.nilavanraj.canarygate.exception.GateBlockedException}
 * is thrown before any business logic executes.
 * After the call, the aspect reports success or failure back to the gate for health tracking.
 *
 * <pre>
 * {@code @GatedFlow("checkout")}
 * public CheckoutResponse checkout(String sessionId, CheckoutRequest req) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GatedFlow {

    /** Short gate name matching the key under {@code canary-gate.flows} in application.yml. */
    String value();
}
