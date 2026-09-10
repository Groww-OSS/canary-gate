package io.github.growwoss.canarygate.store;

import java.util.concurrent.ConcurrentHashMap;

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
