package com.cjl.repopilot.security;

/**
 * Decision returned by RepoPilot's enterprise safety policy.
 */
public enum PolicyDecision {
    ALLOW,
    REQUIRE_APPROVAL,
    BLOCK
}
