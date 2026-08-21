package com.cjl.repopilot.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnterpriseCommandPolicyTest {

    @Test
    void blocksForcePushAndHardReset() {
        assertEquals(PolicyDecision.BLOCK,
                EnterpriseCommandPolicy.assess("git push --force origin main").decision());
        assertEquals(PolicyDecision.BLOCK,
                EnterpriseCommandPolicy.assess("git reset --hard HEAD~1").decision());
    }

    @Test
    void requiresApprovalForRepositoryMutation() {
        assertEquals(PolicyDecision.REQUIRE_APPROVAL,
                EnterpriseCommandPolicy.assess("git push origin feature-x").decision());
        assertEquals(PolicyDecision.REQUIRE_APPROVAL,
                EnterpriseCommandPolicy.assess("rm build/output.txt").decision());
    }

    @Test
    void allowsBuildAndTestCommands() {
        assertEquals(PolicyDecision.ALLOW,
                EnterpriseCommandPolicy.assess("./gradlew test").decision());
        assertEquals(PolicyDecision.ALLOW,
                EnterpriseCommandPolicy.assess("git diff HEAD").decision());
    }
}
