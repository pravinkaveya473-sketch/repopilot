package com.cjl.repopilot.security;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Enterprise command policy for coding-agent shell calls.
 *
 * <p>The policy is intentionally evaluated before user-defined allow rules.
 * A local allow rule cannot make a destructive host command safe.</p>
 */
public final class EnterpriseCommandPolicy {

    private record Rule(Pattern pattern, String category, String reason) {}

    private static final List<Rule> BLOCK_RULES = List.of(
            rule("(?i)(^|[;&|]\\s*)rm\\s+-[^\\n]*r[^\\n]*f[^\\n]*\\s+/(?:\\s|$)", "filesystem", "Refuses recursive deletion of the host root filesystem"),
            rule("(?i)\\bmkfs(?:\\.[a-z0-9]+)?\\b", "filesystem", "Filesystem formatting is never permitted"),
            rule("(?i)\\bdd\\s+[^\\n]*\\bof=/dev/", "device", "Raw writes to block devices are never permitted"),
            rule("(?i)\\b(?:shutdown|reboot|poweroff|halt)\\b", "host", "Host shutdown and reboot commands are blocked"),
            rule("(?i):\\(\\)\\s*\\{\\s*:\\|:&\\s*;\\s*\\}\\s*;\\s*:", "process", "Fork-bomb pattern detected"),
            rule("(?i)\\b(?:curl|wget)\\b[^\\n|]*\\|\\s*(?:ba)?sh\\b", "supply-chain", "Piping downloaded content directly to a shell is blocked"),
            rule("(?i)\\bgit\\s+push\\b[^\\n]*(?:--force(?:-with-lease)?|-f)(?:\\s|$)", "git", "Force-pushing can rewrite shared history"),
            rule("(?i)\\bgit\\s+reset\\s+--hard\\b", "git", "Hard reset can destroy uncommitted work"),
            rule("(?i)\\bgit\\s+clean\\b[^\\n]*(?:-f|-fd|-df)", "git", "Git clean can irreversibly delete untracked files"),
            rule("(?i)\\bgit\\s+(?:checkout|restore)\\s+(?:--\\s+)?\\.\\s*$", "git", "Discarding all working-tree changes is blocked"),
            rule("(?i)\\bgit\\s+branch\\s+-D\\b", "git", "Force-deleting branches is blocked"),
            rule("(?i)\\bchmod\\s+-R\\s+777\\s+/(?:\\s|$)", "permissions", "World-writable permission changes on the host root are blocked"),
            rule("(?i)(?:>|tee\\s+)(?:/dev/sd|/dev/nvme|/etc/shadow|/etc/sudoers)", "host", "Writes to protected host or device files are blocked")
    );

    private static final List<Rule> REVIEW_RULES = List.of(
            rule("(?i)\\bsudo\\b|(^|[;&|]\\s*)su(?:\\s|$)", "privilege", "Privilege escalation requires explicit approval"),
            rule("(?i)\\bgit\\s+(?:push|commit|merge|rebase|cherry-pick|tag|checkout|switch)\\b", "git", "Repository history or branch mutation requires approval"),
            rule("(?i)\\b(?:rm|rmdir|del|erase|remove-item)\\b", "filesystem", "File deletion requires approval"),
            rule("(?i)\\b(?:curl|wget|scp|sftp|ssh|rsync)\\b", "network", "External network access or transfer requires approval"),
            rule("(?i)\\b(?:apt|apt-get|yum|dnf|pacman|brew|choco|winget)\\s+(?:install|remove|upgrade|update)\\b", "dependency", "System package changes require approval"),
            rule("(?i)\\b(?:pip|pip3|npm|pnpm|yarn|gem|cargo)\\s+(?:install|uninstall|add|remove|update)\\b", "dependency", "Dependency graph changes require approval"),
            rule("(?i)\\b(?:docker|podman)\\s+(?:run|exec|rm|rmi|system|compose\\s+(?:up|down))\\b", "container", "Container mutation requires approval"),
            rule("(?i)\\bkubectl\\s+(?:apply|delete|patch|edit|rollout|scale|exec)\\b", "orchestration", "Cluster mutation requires approval"),
            rule("(?i)\\b(?:systemctl|service)\\s+(?:start|stop|restart|enable|disable)\\b", "service", "Service mutation requires approval"),
            rule("(?i)\\b(?:kill|pkill|taskkill)\\b", "process", "Terminating processes requires approval"),
            rule("(?i)\\b(?:mysql|psql|redis-cli|mongosh)\\b[^\\n]*(?:drop|delete|truncate|flushall|flushdb)\\b", "database", "Destructive database operation requires approval"),
            rule("(?i)\\b(?:mvn|./mvnw|gradle|./gradlew)\\s+(?:deploy|publish|release)\\b", "release", "Publishing artifacts requires approval")
    );

    private EnterpriseCommandPolicy() {}

    public static PolicyAssessment assess(String command) {
        if (command == null || command.isBlank()) {
            return PolicyAssessment.block("input", "Empty command is not executable");
        }

        String normalized = command.strip();
        for (Rule rule : BLOCK_RULES) {
            if (rule.pattern.matcher(normalized).find()) {
                return PolicyAssessment.block(rule.category, rule.reason);
            }
        }
        for (Rule rule : REVIEW_RULES) {
            if (rule.pattern.matcher(normalized).find()) {
                return PolicyAssessment.review(RiskLevel.HIGH, rule.category, rule.reason);
            }
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("&&") || lower.contains("||") || lower.contains(";") || lower.contains("|")) {
            return PolicyAssessment.review(RiskLevel.MEDIUM, "compound-command",
                    "Compound commands require review because later segments can change the operation's effect");
        }

        return PolicyAssessment.allow("command", "No enterprise command risk rule matched");
    }

    private static Rule rule(String regex, String category, String reason) {
        return new Rule(Pattern.compile(regex), category, reason);
    }
}
