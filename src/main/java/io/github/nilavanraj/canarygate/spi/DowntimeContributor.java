package io.github.nilavanraj.canarygate.spi;

/**
 * External signal that a flow is experiencing downtime.
 *
 * Implement this interface and register it as a Spring bean to let
 * an external check (DB ping, Redis probe, upstream health endpoint)
 * trigger the gate automatically — without needing admin intervention.
 *
 * The library polls all registered contributors every 30 seconds
 * and opens the gate for the named flow when any contributor returns
 * {@link DowntimeSignal#DEGRADED} or {@link DowntimeSignal#DOWN}.
 *
 * <pre>
 * {@code @Component}
 * public class PaymentDbContributor implements DowntimeContributor {
 *
 *   {@code @Override} public String flowName() { return "checkout"; }
 *
 *   {@code @Override}
 *   public DowntimeSignal check() {
 *     return isReachable(dataSource) ? DowntimeSignal.UP : DowntimeSignal.DOWN;
 *   }
 * }
 * </pre>
 */
public interface DowntimeContributor {

    /** Short gate name this contributor monitors (e.g. {@code "checkout"}). */
    String flowName();

    /** Checks and returns the current signal for this flow. */
    DowntimeSignal check();
}
