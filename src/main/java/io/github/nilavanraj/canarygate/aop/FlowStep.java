package io.github.nilavanraj.canarygate.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// used for record
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlowStep {
    String value();  // step name matching application.yml key, e.g. "payment-initiate"
}
