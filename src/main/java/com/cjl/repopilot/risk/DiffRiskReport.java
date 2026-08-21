package com.cjl.repopilot.risk;

import com.cjl.repopilot.security.RiskLevel;

import java.util.List;

public record DiffRiskReport(
        int score,
        RiskLevel riskLevel,
        int filesChanged,
        int additions,
        int deletions,
        List<String> factors
) {
    public DiffRiskReport {
        factors = factors == null ? List.of() : List.copyOf(factors);
    }
}
