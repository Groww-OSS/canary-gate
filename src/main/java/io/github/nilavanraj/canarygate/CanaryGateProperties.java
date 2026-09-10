package io.github.nilavanraj.canarygate;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "canary-gate")
public class CanaryGateProperties {

    /*
     * canary-gate:
     *
     *   # Global gate evaluation
     *   failure-threshold: 0.3
     *   minimum-throughput: 5
     *   sliding-window-size: 100
     *
     *   # Recovery
     *   half-open:
     *     permitted-number-of-calls-in-half-open-state: 50
     *     max-wait-duration-in-half-open-state: 30s
     *
     *   # Flow hierarchy (vertical only)
     *   flows:
     *     mforder-lumpsum:
     *       open-ttl: 30m
     *       branches:
     *         mf-checkout:
     *           branches:
     *             mf-otp-trigger:
     *               branches:
     *                 payment:
     *                   branches:
     *                     confirmation: {}
     */

    // ── Global Gate Evaluation ──────────────────────────────────────────────

    private double failureThreshold = 0.3;
    private int minimumThroughput = 5;
    private int slidingWindowSize = 100;

    // ── Flow ─────────────────────────────────────────────────────────────────

    private Map<String, FlowProperties> flows = new HashMap<>();

    @Getter
    @Setter
    public static class FlowProperties {
        private Duration openTtl;
        private Map<String, FlowProperties> branches = new HashMap<>();
    }

    // ── Half-Open ────────────────────────────────────────────────────────────

    private HalfOpenProperties halfOpen = new HalfOpenProperties();

    @Getter
    @Setter
    public static class HalfOpenProperties {
        private int permittedNumberOfCallsInHalfOpenState = 50;
        private Duration maxWaitDurationInHalfOpenState =
                Duration.ofSeconds(30);
    }
}
