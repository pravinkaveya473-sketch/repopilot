package com.cjl.repopilot.task;

import java.util.EnumSet;
import java.util.Set;

public enum RepositoryTaskStatus {
    DRAFT,
    PLANNED,
    APPROVED,
    IN_PROGRESS,
    REVIEW_REQUIRED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public Set<RepositoryTaskStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> EnumSet.of(PLANNED, CANCELLED);
            case PLANNED -> EnumSet.of(APPROVED, CANCELLED);
            case APPROVED -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(REVIEW_REQUIRED, FAILED, CANCELLED);
            case REVIEW_REQUIRED -> EnumSet.of(COMPLETED, IN_PROGRESS, FAILED, CANCELLED);
            case FAILED -> EnumSet.of(PLANNED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(RepositoryTaskStatus.class);
        };
    }

    public void requireTransitionTo(RepositoryTaskStatus next) {
        if (next == null || !allowedTransitions().contains(next)) {
            throw new IllegalStateException("Illegal repository task transition: " + this + " -> " + next);
        }
    }
}
