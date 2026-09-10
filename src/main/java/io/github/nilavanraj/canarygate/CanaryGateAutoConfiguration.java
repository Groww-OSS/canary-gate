package io.github.nilavanraj.canarygate;

import io.github.nilavanraj.canarygate.aop.FlowStepAspect;
import io.github.nilavanraj.canarygate.core.GateRegistry;
import io.github.nilavanraj.canarygate.store.GateStateStore;
import io.github.nilavanraj.canarygate.store.InMemoryGateStateStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@EnableConfigurationProperties(CanaryGateProperties.class)
@EnableAspectJAutoProxy
public class CanaryGateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GateStateStore.class)
    public GateStateStore gateStateStore() {
        return new InMemoryGateStateStore();
    }

    @Bean
    public GateRegistry gateRegistry(CanaryGateProperties props,
                                     GateStateStore store,
                                     ApplicationEventPublisher events) {
        return new GateRegistry(props, store, events);
    }

    @Bean
    public CanaryGateService canaryGateService(CanaryGateProperties props,
                                               GateRegistry registry,
                                               GateStateStore store) {
        return new CanaryGateService(props, registry, store);
    }

    @Bean
    public FlowStepAspect flowStepAspect(CanaryGateService service) {
        return new FlowStepAspect(service);
    }
}
