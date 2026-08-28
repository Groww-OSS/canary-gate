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
 */
public interface GateStateStore {

    String get(String key);

    void set(String key, String value);

    void delete(String key);

    /**
     * Atomically adds {@code delta} to the integer value at {@code key} and returns the new value.
     * A missing key is treated as 0, so the first {@code increment(key, 1)} yields 1.
     *
     * <p>Used for pure request-path counters (traffic baseline, per-tier failure/resolved/blocked
     * tallies) where the operation is an unconditional accumulate — never a conditional write.
     * Splitting these out of {@link #compareAndSet} keeps them off the retry-and-throw path: a
     * counter update must never fail a caller's request, and a native atomic add cannot lose an
     * update the way a read-modify-write CAS loop can.
     *
     * <p><b>Contract:</b> this MUST be atomic across every writer that can reach the same key —
     * within a JVM and across pods/instances. Back it with a native atomic add: Redis {@code INCRBY},
     * a JDBC {@code UPDATE ... SET v = v + ?}, {@code AtomicLong}, {@code ConcurrentHashMap.merge},
     * etc. The value at {@code key} is always an integer written only through this method.
     */
    long increment(String key, long delta);

    /**
     * Atomically compares the current value at {@code key} against {@code expected} and,
     * only if they match, replaces it with {@code newValue}.
     *
     * <p>Semantics:
     * <ul>
     *   <li>{@code expected == null} means "key must currently be absent".</li>
     *   <li>{@code newValue == null} means "delete the key" once the compare succeeds.</li>
     *   <li>Returns {@code true} iff the compare matched and the mutation was applied;
     *       otherwise the store is left unchanged and {@code false} is returned.</li>
     * </ul>
     *
     * <p>This is the library's only conditional-write primitive — it backs the fire-once state
     * transitions, the percentage slot-claim ("admit iff admitted &lt; slots"), and the tier-advance
     * guard, none of which an unconditional {@link #increment} can express.
     *
     * <p><b>This method is intentionally abstract: there is no safe default.</b> A plain
     * get + equals + set/delete is racy even for two threads in one JVM, so a default would be a
     * silent footgun that every real implementation has to remember to override. Implement it with a
     * genuine atomic conditional write for your backend — {@code ConcurrentHashMap} CAS in-JVM
     * ({@link InMemoryGateStateStore}), a Redis Lua script or {@code WATCH}/{@code MULTI}/{@code EXEC},
     * an optimistic-locking {@code UPDATE ... WHERE value = ?}, or equivalent — so multi-pod writers
     * cannot reintroduce the read-modify-write races this primitive exists to close.
     */
    boolean compareAndSet(String key, String expected, String newValue);
}
