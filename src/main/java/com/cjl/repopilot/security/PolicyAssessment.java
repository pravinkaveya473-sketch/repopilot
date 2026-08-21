package com.cjl.repopilot.security;

import java.util.Objects;

public record PolicyAssessment(
        PolicyDecision decision,
        RiskLevel riskLevel,
        String category,
        String reason
) {
    public PolicyAssessment {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(riskLevel, "riskLevel");
        category = category == null ? "unknown" : category;
        reason = reason == null ? "" : reason;
    }

    public static PolicyAssessment allow(String category, String reason) {
        return new PolicyAssessment(PolicyDecision.ALLOW, RiskLevel.LOW, category, reason);
    }

    public static PolicyAssessment review(RiskLevel riskLevel, String category, String reason) {
        return new PolicyAssessment(PolicyDecision.REQUIRE_APPROVAL, riskLevel, category, reason);
    }

    public static PolicyAssessment block(String category, String reason) {
        return new PolicyAssessment(PolicyDecision.BLOCK, RiskLevel.CRITICAL, category, reason);
    }
}
