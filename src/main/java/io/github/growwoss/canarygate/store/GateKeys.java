package io.github.growwoss.canarygate.store;

public final class GateKeys {

    private static final String PREFIX = "canary-gate";

    private GateKeys() {}

    // ── Gate state ────────────────────────────────────────────────────────────

    /** Current GateState (CLOSED/OPEN/HALF_OPEN) for this gate. */
    public static String state(String path) {
        return PREFIX + "." + path + ".state";
    }

    /** Epoch millis when the gate last entered OPEN. Read on each OPEN-state request for lazy OPEN → CLOSED expiry. */
    public static String openedAt(String path) {
        return PREFIX + "." + path + ".opened-at";
    }

    // ── Half-open cycle state ─────────────────────────────────────────────────

    /** Number of sessions admitted so far in the current HALF_OPEN cycle. */
    public static String admitted(String path) {
        return PREFIX + "." + path + ".admitted";
    }

    /** Number of admitted sessions in the current cycle that reported FAILURE. */
    public static String halfOpenFailures(String path) {
        return PREFIX + "." + path + ".half-open-failures";
    }

    /** Number of admitted sessions in the current cycle that have reported any outcome. */
    public static String halfOpenResolved(String path) {
        return PREFIX + "." + path + ".half-open-resolved";
    }

    // ── Session tracking ──────────────────────────────────────────────────────

    /** Per-session admission state within a cycle: "admitted" once let through, "resolved" once its outcome is reported. */
    public static String session(String path, String sessionId) {
        return PREFIX + "." + path + ".session." + sessionId;
    }

    /**
     * Newline-joined index of every session id admitted in the CURRENT cycle. Lets the gate
     * enumerate and delete the per-session keys on HALF_OPEN exit, so they don't leak or bleed
     * admission state into the next HALF_OPEN cycle.
     */
    public static String sessionIndex(String path) {
        return PREFIX + "." + path + ".session-index";
    }
}
