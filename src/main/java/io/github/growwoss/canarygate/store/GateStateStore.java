package io.github.growwoss.canarygate.store;

public interface GateStateStore {

    String get(String key);

    void set(String key, String value);

    void delete(String key);
}
