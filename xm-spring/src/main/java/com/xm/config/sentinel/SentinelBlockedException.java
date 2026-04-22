package com.xm.config.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * Thrown when Sentinel blocks the call (flow / circuit breaker / etc.).
 */
public class SentinelBlockedException extends RuntimeException {

    private final String resource;

    public SentinelBlockedException(String resource, BlockException cause) {
        super("Sentinel blocked resource: " + resource, cause);
        this.resource = resource;
    }

    public String getResource() {
        return resource;
    }
}
