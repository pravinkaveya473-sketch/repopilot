package com.cjl.repopilot.risk;

import com.cjl.repopilot.security.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffRiskAnalyzerTest {

    @Test
    void raisesRiskForMigrationSecurityAndCredentialChanges() {
        String diff = """
                diff --git a/src/main/resources/db/migration/V2__user.sql b/src/main/resources/db/migration/V2__user.sql
                --- a/src/main/resources/db/migration/V2__user.sql
                +++ b/src/main/resources/db/migration/V2__user.sql
                +ALTER TABLE users ADD COLUMN role VARCHAR(32);
                diff --git a/src/main/java/demo/SecurityConfig.java b/src/main/java/demo/SecurityConfig.java
                --- a/src/main/java/demo/SecurityConfig.java
                +++ b/src/main/java/demo/SecurityConfig.java
                +String apiKey = "unsafe-value";
                """;

        DiffRiskReport report = new DiffRiskAnalyzer().analyze(diff);

        assertTrue(report.score() >= 40);
        assertTrue(report.riskLevel() == RiskLevel.HIGH || report.riskLevel() == RiskLevel.CRITICAL);
        assertEquals(2, report.filesChanged());
    }
}
