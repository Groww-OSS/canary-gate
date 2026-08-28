package io.github.nilavanraj.canarygate.model;

public enum GatePermission {

    /** Gate is CLOSED and all sub-branches are healthy — full access. */
    ALLOWED,

    /** Main flow is reachable but one or more branches are degraded, or this session
     *  is admitted as a guinea pig in HALF_OPEN percentage mode. */
    PARTIAL,

    /** Gate is OPEN, or HALF_OPEN with this session not admitted. */
    BLOCKED
}
