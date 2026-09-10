package io.github.growwoss.canarygate.core;

import io.github.growwoss.canarygate.CanaryGateProperties;
import io.github.growwoss.canarygate.model.GateState;
import io.github.growwoss.canarygate.model.GateStatus;
import io.github.growwoss.canarygate.store.GateStateStore;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GateRegistry {

    private final Map<String, FlowGate> gates = new HashMap<>();

    private final Map<String, List<FlowGate>> descendants = new HashMap<>();

    public GateRegistry(CanaryGateProperties props,
                        GateStateStore store,
                        ApplicationEventPublisher events) {

        CanaryGateProperties.HalfOpenProperties halfOpen = props.getHalfOpen();
        double failureThreshold = props.getFailureThreshold();

        for (Map.Entry<String, CanaryGateProperties.FlowProperties> entry : props.getFlows().entrySet()) {
            String name = entry.getKey();
            CanaryGateProperties.FlowProperties config = entry.getValue();

            register(name, name, config, halfOpen, failureThreshold, store, events);
            walkBranches(name, config.getBranches(), halfOpen, failureThreshold, store, events);
        }
        buildDescendants();
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    public FlowGate get(String name) {
        FlowGate gate = gates.get(name);
        if (gate == null) {
            throw new IllegalArgumentException(
                "No gate configured for '" + name + "'. " +
                "Check that it is declared under canary-gate.flows in application.yml.");
        }
        return gate;
    }

    public GateState getState(String name) {
        return get(name).getState();
    }

    public GateStatus getStatus(String name) {
        return get(name).getStatus();
    }

    public Collection<FlowGate> all() {
        return gates.values();
    }

    public List<FlowGate> getDescendants(String name) {
        return descendants.getOrDefault(name, List.of());
    }


    private void walkBranches(String parentPath,
                              Map<String, CanaryGateProperties.FlowProperties> branches,
                              CanaryGateProperties.HalfOpenProperties halfOpen,
                              double failureThreshold,
                              GateStateStore store,
                              ApplicationEventPublisher events) {
        for (Map.Entry<String, CanaryGateProperties.FlowProperties> entry : branches.entrySet()) {
            String name = entry.getKey();
            CanaryGateProperties.FlowProperties config = entry.getValue();
            String path = parentPath + "." + name;

            register(name, path, config, halfOpen, failureThreshold, store, events);
            walkBranches(path, config.getBranches(), halfOpen, failureThreshold, store, events);
        }
    }

    private void register(String name,
                          String path,
                          CanaryGateProperties.FlowProperties config,
                          CanaryGateProperties.HalfOpenProperties halfOpen,
                          double failureThreshold,
                          GateStateStore store,
                          ApplicationEventPublisher events) {
        validateName(name);
        gates.put(name, new FlowGate(name, path, config, halfOpen, failureThreshold, store, events));
    }

    private void buildDescendants() {
        for (FlowGate gate : gates.values()) {
            String prefix = gate.getPath() + ".";
            List<FlowGate> children = gates.values().stream()
                    .filter(other -> other.getPath().startsWith(prefix))
                    .collect(Collectors.toCollection(ArrayList::new));
            descendants.put(gate.getName(), children);
        }
    }

    private void validateName(String name) {
        if (gates.containsKey(name)) {
            throw new IllegalStateException(
                "Duplicate gate name '" + name + "'. " +
                "Leaf names must be unique across the whole canary-gate.flows tree.");
        }
    }
}
