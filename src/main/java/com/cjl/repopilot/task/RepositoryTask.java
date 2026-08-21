package com.cjl.repopilot.task;

import com.cjl.repopilot.security.RiskLevel;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RepositoryTask(
        String id,
        String title,
        String description,
        String repositoryRoot,
        String baseBranch,
        String workBranch,
        RiskLevel riskLevel,
        RepositoryTaskStatus status,
        String createdBy,
        String approvedBy,
        String createdAt,
        String updatedAt,
        long version
) {
    public RepositoryTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        description = description == null ? "" : description;
        baseBranch = baseBranch == null || baseBranch.isBlank() ? "main" : baseBranch;
        workBranch = workBranch == null || workBranch.isBlank() ? "repopilot/task-" + id : workBranch;
        createdBy = createdBy == null || createdBy.isBlank() ? "local-user" : createdBy;
        approvedBy = approvedBy == null ? "" : approvedBy;
    }

    public static RepositoryTask create(
            String title,
            String description,
            String repositoryRoot,
            String baseBranch,
            RiskLevel riskLevel,
            String createdBy
    ) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        return new RepositoryTask(
                id,
                title.strip(),
                description,
                repositoryRoot,
                baseBranch,
                "repopilot/task-" + id.substring(0, 8),
                riskLevel == null ? RiskLevel.MEDIUM : riskLevel,
                RepositoryTaskStatus.DRAFT,
                createdBy,
                "",
                now,
                now,
                0
        );
    }

    public RepositoryTask transition(RepositoryTaskStatus next, String actor) {
        status.requireTransitionTo(next);
        String approver = next == RepositoryTaskStatus.APPROVED
                ? normalizeActor(actor)
                : approvedBy;
        return new RepositoryTask(
                id, title, description, repositoryRoot, baseBranch, workBranch,
                riskLevel, next, createdBy, approver, createdAt,
                Instant.now().toString(), version + 1
        );
    }

    private static String normalizeActor(String actor) {
        return actor == null || actor.isBlank() ? "local-approver" : actor.strip();
    }
}
