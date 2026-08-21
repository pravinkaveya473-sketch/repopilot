package com.cjl.repopilot.risk;

import com.cjl.repopilot.security.RiskLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Static risk scoring for unified git diff text.
 */
public final class DiffRiskAnalyzer {

    public DiffRiskReport analyze(String unifiedDiff) {
        if (unifiedDiff == null || unifiedDiff.isBlank()) {
            return new DiffRiskReport(0, RiskLevel.LOW, 0, 0, 0,
                    java.util.List.of("No code changes detected"));
        }

        int additions = 0;
        int deletions = 0;
        int score = 0;
        Set<String> files = new HashSet<>();
        var factors = new ArrayList<String>();

        for (String line : unifiedDiff.split("\\R")) {
            if (line.startsWith("+++ b/")) {
                files.add(line.substring("+++ b/".length()).strip());
            } else if (line.startsWith("--- a/")) {
                files.add(line.substring("--- a/".length()).strip());
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                additions++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                deletions++;
            }
        }

        int filesChanged = files.size();
        if (filesChanged >= 20) {
            score += 25;
            factors.add("Large change set: " + filesChanged + " files");
        } else if (filesChanged >= 8) {
            score += 12;
            factors.add("Medium change set: " + filesChanged + " files");
        }

        int changedLines = additions + deletions;
        if (changedLines >= 1000) {
            score += 25;
            factors.add("More than 1000 changed lines");
        } else if (changedLines >= 300) {
            score += 12;
            factors.add("More than 300 changed lines");
        }

        for (String file : files) {
            String lower = file.toLowerCase(Locale.ROOT);
            if (lower.contains("migration") || lower.endsWith(".sql")) {
                score += 18;
                factors.add("Database schema or SQL changed: " + file);
            }
            if (lower.startsWith(".github/workflows/") || lower.contains("gitlab-ci")) {
                score += 20;
                factors.add("CI/CD workflow changed: " + file);
            }
            if (lower.endsWith("pom.xml") || lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts")) {
                score += 12;
                factors.add("Build dependencies or build logic changed: " + file);
            }
            if (lower.contains("security") || lower.contains("auth") || lower.contains("permission")) {
                score += 15;
                factors.add("Authentication or authorization code changed: " + file);
            }
            if (lower.endsWith("application.yml") || lower.endsWith("application.yaml")
                    || lower.endsWith("application.properties") || lower.endsWith("compose.yaml")) {
                score += 10;
                factors.add("Runtime or deployment configuration changed: " + file);
            }
            if (lower.endsWith(".pem") || lower.endsWith(".key") || lower.contains(".env")) {
                score += 40;
                factors.add("Potential secret material changed: " + file);
            }
        }

        String lowerDiff = unifiedDiff.toLowerCase(Locale.ROOT);
        if (lowerDiff.contains("@transactional") || lowerDiff.contains("synchronized")
                || lowerDiff.contains("reentrantlock") || lowerDiff.contains("volatile ")) {
            score += 10;
            factors.add("Transaction or concurrency behavior changed");
        }
        if (lowerDiff.matches("(?s).*(password|secret|api[_-]?key)\\s*[=:].*")) {
            score += 30;
            factors.add("Possible hard-coded credential pattern detected");
        }
        if (deletions > additions * 2L && deletions >= 100) {
            score += 12;
            factors.add("Deletion-heavy change set");
        }

        score = Math.min(score, 100);
        RiskLevel level = score >= 70 ? RiskLevel.CRITICAL
                : score >= 40 ? RiskLevel.HIGH
                : score >= 15 ? RiskLevel.MEDIUM
                : RiskLevel.LOW;

        if (factors.isEmpty()) {
            factors.add("No elevated-risk pattern detected");
        }
        return new DiffRiskReport(score, level, filesChanged, additions, deletions, factors);
    }

    public DiffRiskReport analyzeRepository(Path repositoryRoot) throws IOException, InterruptedException {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Process process = new ProcessBuilder("git", "-C", root.toString(), "diff", "--no-ext-diff", "--unified=0", "HEAD")
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("git diff timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException("git diff failed: " + output.strip());
        }
        return analyze(output);
    }
}
