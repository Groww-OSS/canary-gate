package io.github.nilavanraj.canarygate.store;

/**
 * Pluggable storage backend for all runtime gate state.
 *
 * Implement this interface and register it as a Spring bean to replace
 * the default in-memory store. Auto-configuration backs off automatically.
 *
 *   @Bean
 *   public GateStateStore redisGateStateStore(RedisTemplate<String, String> redis) {
 *       return new RedisGateStateStore(redis);
 *   }
 *
 * NOTE: no atomic increment/compareAndSet on this interface by design — FlowGate's methods
 * are all `synchronized`, so within a JVM plain get/set is already race-free, and this
 * project isn't targeting multi-instance/shared-store correctness right now. If that changes
 * (e.g. a Redis-backed store shared across pods), counters and conditional writes will need
 * to move back to real atomic primitives (INCRBY, Lua/WATCH-MULTI-EXEC, optimistic UPDATE),
 * since plain get-then-set is racy the moment more than one process can reach the same key.
 */
public interface GateStateStore {

    String get(String key);

    void set(String key, String value);

    void delete(String key);
}
