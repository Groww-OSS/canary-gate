package io.github.nilavanraj.canarygate.spi;

/**
 * Signal returned by a {@link DowntimeContributor}.
 *
 * Both DOWN and DEGRADED open the gate. The distinction exists for callers that
 * want to surface a richer status in dashboards or alerts — the library treats them
 * identically when deciding gate state.
 */
public enum DowntimeSignal {

    /** Service is reachable — no gate action. */
    UP,

    /** Service is partially impaired but still responding — gate opens. */
    DEGRADED,

    /** Service is unreachable or completely down — gate opens. */
    DOWN
}
