package io.github.nilavanraj.canarygate.spi;

/**
 * Extracts a session identifier from the method arguments of a {@code @GatedFlow}-annotated call.
 *
 * The default implementation picks the first {@code String} argument.
 * Override this bean to integrate with HTTP session IDs, JWT subjects,
 * or any other session identity.
 */
@FunctionalInterface
public interface SessionIdResolver {

    String resolve(Object[] methodArgs);
}
