package com.cjl.repopilot.security;

/**
 * Unified risk level used by command, path and diff assessments.
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean requiresApproval() {
        return this == HIGH || this == CRITICAL;
    }
}
