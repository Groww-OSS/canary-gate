# canary-gate

A Spring Boot library that protects your application when an important business flow starts failing.

Think of it as a **safety gate for your application**.

When a flow starts failing frequently, `canary-gate` temporarily stops new requests from entering that flow. After some time, it allows a small number of requests to test whether the system has recovered. If they succeed, traffic is allowed again.

## How it works

```text
                 Too many failures
                       │
                       ▼
                    CLOSED
                       │
                       ▼
                     OPEN
              Block new requests
                       │
                 Wait for some time
                       │
                       ▼
                  HALF_OPEN
             Allow a few test requests
                  │         │
              Success     Failure
                  │         │
                  ▼         ▼
               CLOSED      OPEN
```

### CLOSED

Everything is working normally.

Requests are allowed to proceed.

### OPEN

The flow is experiencing too many failures.

New requests are blocked to prevent making the problem worse.

### HALF_OPEN

The system has had some time to recover.

A small number of requests are allowed through to check if the flow is healthy again.

If they succeed → **CLOSED**

If they fail → **OPEN**

---

## Why use canary-gate?

Imagine you have a checkout flow:

```text
Checkout
   │
   ├── Payment
   ├── Order Creation
   └── Confirmation
```

If the payment service suddenly starts failing, allowing every new checkout request to continue may create more failures and put additional load on the system.

`canary-gate` can detect the failures and temporarily stop new checkout requests.

Once the payment service recovers, a few requests are allowed through as **canary requests**. If they succeed, normal traffic resumes.

---

## Installation

Add the dependency to your Spring Boot application:

```xml
<dependency>
    <groupId>io.github.nilavanraj</groupId>
    <artifactId>canary-gate</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

---

## Configuration

A basic configuration looks like this:

```yaml
canary-gate:
  failure-threshold: 0.5
  minimum-throughput: 5
  sliding-window-size: 100

  half-open:
    permitted-number-of-calls-in-half-open-state: 3
    max-wait-duration-in-half-open-state: 30s

  flows:
    checkout:
      open-ttl: 5m
```

### What do these settings mean?

| Setting                                        | Meaning                                             |
| ---------------------------------------------- | --------------------------------------------------- |
| `failure-threshold`                            | Failure rate required to open the gate              |
| `minimum-throughput`                           | Minimum number of requests before checking failures |
| `sliding-window-size`                          | Number of recent requests used to calculate health  |
| `open-ttl`                                     | How long the gate stays open                        |
| `permitted-number-of-calls-in-half-open-state` | Number of test requests allowed during recovery     |

---

## Basic usage

Inject `CanaryGateService` into your service:

```java
@Service
public class CheckoutService {

    private final CanaryGateService canaryGate;

    public CheckoutService(CanaryGateService canaryGate) {
        this.canaryGate = canaryGate;
    }
}
```

Before starting your flow, check whether requests are allowed:

```java
public CheckoutResponse checkout(String userId, CheckoutRequest request) {

    GatePermission permission =
            canaryGate.isAllowed("checkout", userId);

    if (permission == GatePermission.BLOCKED) {
        throw new GateBlockedException("checkout");
    }

    // Continue with checkout
    return doCheckout(request);
}
```

---

## Monitoring individual steps

You can also monitor individual steps in your flow using `@FlowStep`.

```java
@FlowStep("payment")
public PaymentResult makePayment(PaymentRequest request) {
    // Payment logic
}
```

If the payment step starts failing frequently, `canary-gate` can detect the problem and take action based on your configuration.

---

## Gate permissions

`isAllowed()` returns one of these values:

| Permission | Meaning                                                   |
| ---------- | --------------------------------------------------------- |
| `ALLOWED`  | Request can proceed normally                              |
| `PARTIAL`  | Request can proceed, but part of the flow may be degraded |
| `BLOCKED`  | Request should not proceed                                |

---

## Multiple application instances

By default, gate state is stored **in memory**.

This works well when your application runs as a single instance.

If your application runs on multiple pods/instances:

```text
        ┌── Pod 1
Request ├── Pod 2
        └── Pod 3
             │
             ▼
        Shared Gate State
```

you should provide a shared `GateStateStore`, such as Redis.

```java
@Bean
public GateStateStore gateStateStore(RedisTemplate<String, String> redis) {
    return new RedisGateStateStore(redis);
}
```

This allows multiple application instances to share the same gate state.

> When using a distributed store such as Redis, the implementation should use atomic operations to keep gate decisions correct when multiple instances update the state at the same time.

---

## Events

You can listen for gate state changes:

```java
@EventListener
public void onGateStateChanged(GateStateChangedEvent event) {
    log.info(
        "Gate {} changed: {} → {}",
        event.getFlowName(),
        event.getPrevious(),
        event.getNext()
    );
}
```

This can be useful for logging, monitoring, or sending alerts.

---

## In short

`canary-gate` helps protect critical business flows from repeated failures.

```text
Normal traffic
     ↓
Failures increase
     ↓
Gate opens
     ↓
New traffic is blocked
     ↓
System gets time to recover
     ↓
A few requests are tested
     ↓
Healthy → traffic resumes
Unhealthy → keep blocking
```

**Fail fast. Recover safely. Resume gradually.**
