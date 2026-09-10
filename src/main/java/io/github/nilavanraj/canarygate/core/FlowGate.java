package io.github.nilavanraj.canarygate.core;

import io.github.nilavanraj.canarygate.CanaryGateProperties;
import io.github.nilavanraj.canarygate.event.GateStateChangedEvent;
import io.github.nilavanraj.canarygate.model.GatePermission;
import io.github.nilavanraj.canarygate.model.GateState;
import io.github.nilavanraj.canarygate.model.GateStatus;
import io.github.nilavanraj.canarygate.store.GateKeys;
import io.github.nilavanraj.canarygate.store.GateStateStore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * failureThreshold comes from the global CanaryGateProperties.failureThreshold — there is
 * no per-flow override on FlowProperties, so every gate shares the one configured value.
 */
@Getter
@RequiredArgsConstructor
public class FlowGate {

    private final String name;
    private final String path;
    private final CanaryGateProperties.FlowProperties config;
    private final CanaryGateProperties.HalfOpenProperties halfOpen;
    private final double failureThreshold;
    private final GateStateStore store;
    private final ApplicationEventPublisher events;

    // ── State reads ───────────────────────────────────────────────────────────

    public GateState getState() {
        String raw = store.get(GateKeys.state(path));
        return raw == null ? GateState.CLOSED : GateState.valueOf(raw);
    }

    public GateStatus getStatus() {
        int admitted = parseOrZero(store.get(GateKeys.admitted(path)));
        int failures = parseOrZero(store.get(GateKeys.halfOpenFailures(path)));
        return new GateStatus(name, getState(), admitted, failures);
    }

    // ── State transitions ─────────────────────────────────────────────────────
    public synchronized void transitionTo(GateState next, String reason) {
        GateState current = getState();
        if (current == next) return;
        validateTransition(current, next);

        if (next == GateState.HALF_OPEN) {
            initHalfOpenState();
        } else if (current == GateState.HALF_OPEN) {
            clearHalfOpenState();
        }

        // Stamp / clear the OPEN entry time for lazy OPEN → CLOSED expiry (checked on the request path).
        if (next == GateState.OPEN) {
            store.set(GateKeys.openedAt(path), String.valueOf(System.currentTimeMillis()));
        } else if (current == GateState.OPEN) {
            store.delete(GateKeys.openedAt(path));
        }

        store.set(GateKeys.state(path), next.name());
        events.publishEvent(new GateStateChangedEvent(name, current, next, reason));
    }

    // ── Admission check ───────────────────────────────────────────────────────


    public synchronized GatePermission checkAdmission(String sessionId) {
        String sessionKey = GateKeys.session(path, sessionId);

        // Already admitted in this HALF_OPEN cycle
        String existing = store.get(sessionKey);
        if (isAdmitted(existing) || "resolved".equals(existing)) return GatePermission.PARTIAL;

        int budget = deriveBudget();
        int admitted = parseOrZero(store.get(GateKeys.admitted(path)));

        if (admitted >= budget) return GatePermission.BLOCKED;

        // Value carries the admit time ("admitted:<epochMillis>") so lazy per-session expiry can
        // fail a guinea pig that never reports back, without a running timer.
        store.set(sessionKey, ADMITTED_PREFIX + System.currentTimeMillis());
        store.set(GateKeys.admitted(path), String.valueOf(admitted + 1));
        addToSessionIndex(sessionId);
        return GatePermission.PARTIAL;
    }

    // ── Outcome reporting ──────────────────────────────────────────────────────

    /**
     * Records a session outcome in HALF_OPEN mode.
     * Once every admitted session has reported back, decides CLOSED vs OPEN for the whole
     * round based on the round's failure rate.
     */
    public synchronized void reportOutcome(String sessionId, boolean success) {
        reportOutcome(sessionId, success, "auto-revert");
    }

    /**
     * Same as {@link #reportOutcome(String, boolean)} but lets the caller set the revert reason.
     * Used by the TTL path to emit {@code "ttl-expiry"} instead of {@code "auto-revert"}.
     */
    public synchronized void reportOutcome(String sessionId, boolean success, String revertReason) {
        if (getState() != GateState.HALF_OPEN) return;

        String sessionKey = GateKeys.session(path, sessionId);
        if (!isAdmitted(store.get(sessionKey))) return; // never admitted, or already resolved

        store.set(sessionKey, "resolved");
        int resolved = parseOrZero(store.get(GateKeys.halfOpenResolved(path))) + 1;
        store.set(GateKeys.halfOpenResolved(path), String.valueOf(resolved));

        int failures = parseOrZero(store.get(GateKeys.halfOpenFailures(path)));
        if (!success) {
            failures += 1;
            store.set(GateKeys.halfOpenFailures(path), String.valueOf(failures));
        }

        int admitted = parseOrZero(store.get(GateKeys.admitted(path)));
        if (admitted == 0 || resolved < admitted) return; // round not finished yet

        double failureRate = (double) failures / admitted;
        if (failureRate >= failureThreshold) {
            transitionTo(GateState.OPEN, revertReason);
        } else {
            transitionTo(GateState.CLOSED, "auto-close");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void initHalfOpenState() {
        // Sweep any per-session keys left over from a prior HALF_OPEN cycle that did not clean up
        // (e.g. a crash mid-cycle), so a returning session id can't inherit stale admission state.
        clearSessionKeys();

        store.set(GateKeys.admitted(path), "0");
        store.set(GateKeys.halfOpenFailures(path), "0");
        store.set(GateKeys.halfOpenResolved(path), "0");
    }

    private void clearHalfOpenState() {
        clearSessionKeys();
        store.delete(GateKeys.admitted(path));
        store.delete(GateKeys.halfOpenFailures(path));
        store.delete(GateKeys.halfOpenResolved(path));
    }

    private int deriveBudget() {
        return halfOpen.getPermittedNumberOfCallsInHalfOpenState();
    }

    // ── Lazy per-session expiry ────────────────────────────────────────────────

    /**
     * Fails every guinea-pig session that was admitted more than {@code maxWait} ago but never
     * reported an outcome. Called on the request path (HALF_OPEN admission) instead of a running
     * timer, so a session that goes silent can't stall the round forever. No-op outside HALF_OPEN.
     */
    public synchronized void sweepExpiredSessions(java.time.Duration maxWait) {
        if (maxWait == null || getState() != GateState.HALF_OPEN) return;

        String index = store.get(GateKeys.sessionIndex(path));
        if (index == null || index.isEmpty()) return;

        long now = System.currentTimeMillis();
        long maxMillis = maxWait.toMillis();

        for (String sessionId : index.split(SESSION_INDEX_DELIM, -1)) {
            if (sessionId.isEmpty()) continue;
            String val = store.get(GateKeys.session(path, sessionId));
            if (!isAdmitted(val)) continue; // resolved or gone
            long admittedAt = parseAdmitTime(val);
            if (now - admittedAt >= maxMillis) {
                reportOutcome(sessionId, false, "ttl-expiry");
            }
        }
    }

    // ── Session-key bookkeeping ────────────────────────────────────────────────
    // Session ids are arbitrary strings; the index joins them with '\n' (newline), a
    // character that cannot appear in a well-formed session id here. A session id that
    // does contain a newline is still admitted — only its key cleanup is skipped.

    private static final String SESSION_INDEX_DELIM = "\n";

    /** Per-session value written on admission: the prefix plus the admit epoch millis. */
    private static final String ADMITTED_PREFIX = "admitted:";

    /** True if the stored session value represents an admitted-but-unresolved guinea pig. */
    private static boolean isAdmitted(String value) {
        return value != null && value.startsWith(ADMITTED_PREFIX);
    }

    /** Extracts the admit epoch millis from an "admitted:<millis>" value; 0 if unparseable. */
    private static long parseAdmitTime(String value) {
        if (!isAdmitted(value)) return 0L;
        try {
            return Long.parseLong(value.substring(ADMITTED_PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Appends a session id to the current-cycle index so its per-session key can be swept later. */
    private void addToSessionIndex(String sessionId) {
        if (sessionId.contains(SESSION_INDEX_DELIM)) return; // unindexable id — skip (see note above)
        String current = store.get(GateKeys.sessionIndex(path));
        String next = (current == null || current.isEmpty()) ? sessionId : current + SESSION_INDEX_DELIM + sessionId;
        store.set(GateKeys.sessionIndex(path), next);
    }

    /** Deletes every per-session key recorded in the index, then the index itself. Idempotent. */
    private void clearSessionKeys() {
        String index = store.get(GateKeys.sessionIndex(path));
        if (index != null && !index.isEmpty()) {
            for (String sessionId : index.split(SESSION_INDEX_DELIM, -1)) {
                if (!sessionId.isEmpty()) store.delete(GateKeys.session(path, sessionId));
            }
        }
        store.delete(GateKeys.sessionIndex(path));
    }

    private void validateTransition(GateState from, GateState to) {
        boolean illegal = (from == GateState.CLOSED && to == GateState.HALF_OPEN);

        if (illegal) {
            throw new IllegalStateException(
                "Illegal gate transition for '" + name + "': " + from + " → " + to);
        }
    }

    private static int parseOrZero(String value) {
        return value == null ? 0 : Integer.parseInt(value);
    }
}
