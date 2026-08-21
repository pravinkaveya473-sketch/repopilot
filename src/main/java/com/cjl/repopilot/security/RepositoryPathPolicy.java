package com.cjl.repopilot.security;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Protects repository boundaries and sensitive files from direct AI writes.
 */
public final class RepositoryPathPolicy {

    private static final List<String> BLOCKED_FILE_NAMES = List.of(
            ".env", ".env.local", ".env.production", "id_rsa", "id_ed25519",
            "credentials", "credentials.json", "secrets.yaml", "secrets.yml"
    );

    private static final List<String> BLOCKED_SUFFIXES = List.of(
            ".pem", ".p12", ".pfx", ".jks", ".keystore", ".key"
    );

    private static final List<String> REVIEW_FILE_NAMES = List.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "dockerfile", "compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml",
            "application.yml", "application.yaml", "application.properties"
    );

    private RepositoryPathPolicy() {}

    public static PolicyAssessment assessWrite(Path repositoryRoot, String requestedPath) {
        if (repositoryRoot == null) {
            return PolicyAssessment.block("path", "Repository root is required for write operations");
        }
        if (requestedPath == null || requestedPath.isBlank()) {
            return PolicyAssessment.block("path", "Target path is empty");
        }

        final Path root;
        final Path target;
        try {
            root = repositoryRoot.toAbsolutePath().normalize();
            Path raw = Path.of(requestedPath);
            target = (raw.isAbsolute() ? raw : root.resolve(raw)).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            return PolicyAssessment.block("path", "Target path is invalid: " + ex.getMessage());
        }

        if (!target.startsWith(root)) {
            return PolicyAssessment.block("path-boundary", "Writes outside the selected repository are blocked");
        }

        Path relative = root.relativize(target);
        String unix = relative.toString().replace('\\', '/');
        String lower = unix.toLowerCase(Locale.ROOT);
        String fileName = target.getFileName() == null ? "" : target.getFileName().toString().toLowerCase(Locale.ROOT);

        if (lower.equals(".git") || lower.startsWith(".git/")) {
            return PolicyAssessment.block("git-internals", "Direct writes to .git internals are blocked");
        }
        if (lower.equals(".repopilot/audit") || lower.startsWith(".repopilot/audit/")) {
            return PolicyAssessment.block("audit", "Agent tools cannot rewrite RepoPilot audit evidence");
        }
        if (BLOCKED_FILE_NAMES.contains(fileName) || BLOCKED_SUFFIXES.stream().anyMatch(fileName::endsWith)) {
            return PolicyAssessment.block("secret", "Potential credential or private-key file is protected");
        }
        if (lower.startsWith(".github/workflows/") || lower.startsWith(".gitlab-ci")) {
            return PolicyAssessment.review(RiskLevel.HIGH, "ci-cd", "CI/CD workflow changes require approval");
        }
        if (lower.contains("/db/migration/") || lower.startsWith("db/migration/")
                || lower.contains("/migrations/") || lower.startsWith("migrations/")) {
            return PolicyAssessment.review(RiskLevel.HIGH, "database-migration",
                    "Database migration changes require approval and rollback review");
        }
        if (REVIEW_FILE_NAMES.contains(fileName)) {
            return PolicyAssessment.review(RiskLevel.HIGH, "build-config",
                    "Build, deployment or runtime configuration changes require approval");
        }

        return PolicyAssessment.allow("repository-file", "Path stays inside the repository and is not protected");
    }
}
