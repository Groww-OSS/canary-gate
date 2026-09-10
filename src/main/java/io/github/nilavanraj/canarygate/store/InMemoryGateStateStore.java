package io.github.nilavanraj.canarygate.store;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Default GateStateStore backed by a ConcurrentHashMap.
 * State is local to this JVM and lost on restart.
 * Suitable for single-instance services and local development.
 */
public class InMemoryGateStateStore implements GateStateStore {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        return store.get(key);
    }

    @Override
    public void set(String key, String value) {
        store.put(key, value);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    /** Test helper — wipes all keys. */
    public void clear() {
        store.clear();
    }
}
