package io.github.growwoss.canarygate.event;

import io.github.growwoss.canarygate.model.GateState;
import lombok.Getter;

import java.time.Instant;

@Getter
public class GateStateChangedEvent {

    private final String flowName;
    private final GateState previous;
    private final GateState next;
    private final String reason;
    private final Instant occurredAt;

    public GateStateChangedEvent(String flowName, GateState previous, GateState next, String reason) {
        this.flowName   = flowName;
        this.previous   = previous;
        this.next       = next;
        this.reason     = reason;
        this.occurredAt = Instant.now();
    }

    @Override
    public String toString() {
        return "GateStateChangedEvent{flow='" + flowName + "', " +
               previous + " → " + next + ", reason='" + reason + "'}";
    }
}
