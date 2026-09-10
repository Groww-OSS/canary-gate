package io.github.growwoss.canarygate.exception;

import lombok.Getter;

public class GateBlockedException extends RuntimeException {

    @Getter
    private final String gateName;

    public GateBlockedException(String gateName) {
        super("Gate '" + gateName + "' is OPEN — flow entry blocked");
        this.gateName = gateName;
    }
}
