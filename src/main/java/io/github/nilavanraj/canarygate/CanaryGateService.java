package io.github.nilavanraj.canarygate;

import io.github.nilavanraj.canarygate.core.FlowGate;
import io.github.nilavanraj.canarygate.core.GateRegistry;
import io.github.nilavanraj.canarygate.core.StepHealthRecord;
import io.github.nilavanraj.canarygate.event.GateStateChangedEvent;
import io.github.nilavanraj.canarygate.model.GatePermission;
import io.github.nilavanraj.canarygate.model.GateState;
import io.github.nilavanraj.canarygate.model.StepOutcome;
import io.github.nilavanraj.canarygate.store.GateKeys;
import io.github.nilavanraj.canarygate.store.GateStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class CanaryGateService {

    private final CanaryGateProperties props;
    private final GateRegistry registry;
    private final GateStateStore store;

    private final Map<String, StepHealthRecord> stepHealth = new ConcurrentHashMap<>();

    // Step health
    public void record(String stepName, StepOutcome outcome) {
        StepHealthRecord rec = stepHealth.computeIfAbsent(stepName,
                k -> new StepHealthRecord(props.getSlidingWindowSize()));
        rec.record(outcome);
        checkHealthAndAct(stepName, rec);
    }

    public double getFailureRate(String stepName) {
        StepHealthRecord r = stepHealth.get(stepName);
        return r == null ? 0.0 : r.failureRate();
    }

    private void checkHealthAndAct(String stepName, StepHealthRecord rec) {
        FlowGate gate;
        try { gate = registry.get(stepName); } catch (IllegalArgumentException e) { return; }

        if (rec.getTotal() < props.getMinimumThroughput()) return;
        if (rec.failureRate() >= gate.getFailureThreshold()
                && registry.getState(stepName) == GateState.CLOSED) {
            gate.transitionTo(GateState.OPEN, "step-health");
        }
    }

    // Admission

    /**
     * OPEN + openTtl elapsed          -> lazily close, then evaluate as CLOSED
     * OPEN + not yet elapsed          -> BLOCKED
     * HALF_OPEN                       -> sweep stale sessions, then checkAdmission
     * CLOSED + a descendant degraded  -> PARTIAL
     * CLOSED + all descendants CLOSED -> ALLOWED
     */
    public GatePermission isAllowed(String name, String sessionId) {
        FlowGate gate = registry.get(name);
        GateState state = gate.getState();

        if (state == GateState.OPEN) {
            if (!expireOpenIfDue(gate)) return GatePermission.BLOCKED;
            state = gate.getState(); // fell through: re-read (now CLOSED)
        }

        if (state == GateState.HALF_OPEN) {
            gate.sweepExpiredSessions(gate.getHalfOpen().getMaxWaitDurationInHalfOpenState());
            return gate.checkAdmission(sessionId);
        }

        boolean degraded = registry.getDescendants(name).stream()
                .anyMatch(g -> g.getState() != GateState.CLOSED);
        return degraded ? GatePermission.PARTIAL : GatePermission.ALLOWED;
    }

    // Outcome reporting on test users
    public void reportSuccess(String name, String sessionId) {
        registry.get(name).reportOutcome(sessionId, true);
    }

    public void reportFailure(String name, String sessionId) {
        registry.get(name).reportOutcome(sessionId, false);
    }

    // Event listener

    @EventListener
    public void onGateStateChanged(GateStateChangedEvent event) {
        // Gate opened — clear stale step health so recovery re-evaluates from fresh outcomes.
        if (event.getNext() == GateState.OPEN) {
            resetStepHealth(event.getFlowName());
        }
    }

    // ttl expire

    private boolean expireOpenIfDue(FlowGate gate) {
        Duration openTtl = gate.getConfig().getOpenTtl();
        if (openTtl == null) return false; // no auto-close configured

        String raw = store.get(GateKeys.openedAt(gate.getPath()));
        if (raw == null) return false; // no timestamp — leave it OPEN

        long openedAt;
        try { openedAt = Long.parseLong(raw); } catch (NumberFormatException e) { return false; }

        if (System.currentTimeMillis() - openedAt >= openTtl.toMillis()) {
            gate.transitionTo(GateState.CLOSED, "ttl-expiry");
            return true;
        }
        return false;
    }

    private void resetStepHealth(String gateName) {
        stepHealth.remove(gateName);
    }
}
