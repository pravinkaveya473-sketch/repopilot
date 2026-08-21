package com.cjl.repopilot.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryPathPolicyTest {

    @Test
    void blocksWritesOutsideRepositoryAndSecrets(@TempDir Path root) {
        assertEquals(PolicyDecision.BLOCK,
                RepositoryPathPolicy.assessWrite(root, root.resolve("..").resolve("outside.txt").toString()).decision());
        assertEquals(PolicyDecision.BLOCK,
                RepositoryPathPolicy.assessWrite(root, ".env").decision());
        assertEquals(PolicyDecision.BLOCK,
                RepositoryPathPolicy.assessWrite(root, ".git/config").decision());
    }

    @Test
    void requiresApprovalForMigrationAndBuildFiles(@TempDir Path root) {
        assertEquals(PolicyDecision.REQUIRE_APPROVAL,
                RepositoryPathPolicy.assessWrite(root, "src/main/resources/db/migration/V2__change.sql").decision());
        assertEquals(PolicyDecision.REQUIRE_APPROVAL,
                RepositoryPathPolicy.assessWrite(root, "build.gradle.kts").decision());
    }

    @Test
    void allowsNormalSourceFile(@TempDir Path root) {
        assertEquals(PolicyDecision.ALLOW,
                RepositoryPathPolicy.assessWrite(root, "src/main/java/demo/App.java").decision());
    }
}
