package io.github.nilavanraj.canarygate.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GateStatus {

    private final String name;
    private final GateState state;
    private final int admitted;
    private final int failures;

    public double failureRate() {
        return admitted == 0 ? 0.0 : (double) failures / admitted;
    }

    @Override
    public String toString() {
        return "GateStatus{name='" + name + "', state=" + state +
               ", admitted=" + admitted + ", failures=" + failures +
               ", failureRate=" + failureRate() + "}";
    }
}
