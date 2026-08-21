package com.cjl.repopilot.task;

import com.cjl.repopilot.security.RiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryTaskServiceTest {

    @Test
    void followsApprovalAndReviewWorkflow(@TempDir Path repository) {
        RepositoryTaskService service = new RepositoryTaskService(repository);
        RepositoryTask task = service.create("Add repository search", "Implement symbol index", "main",
                RiskLevel.HIGH, "developer");

        task = service.transition(task.id(), RepositoryTaskStatus.PLANNED, "developer");
        task = service.transition(task.id(), RepositoryTaskStatus.APPROVED, "tech-lead");
        task = service.transition(task.id(), RepositoryTaskStatus.IN_PROGRESS, "developer");
        task = service.transition(task.id(), RepositoryTaskStatus.REVIEW_REQUIRED, "developer");
        task = service.transition(task.id(), RepositoryTaskStatus.COMPLETED, "reviewer");

        assertEquals(RepositoryTaskStatus.COMPLETED, task.status());
        assertEquals("tech-lead", task.approvedBy());
        assertEquals(5, task.version());
        assertEquals(1, service.list().size());
    }

    @Test
    void rejectsSkippingRequiredStates(@TempDir Path repository) {
        RepositoryTaskService service = new RepositoryTaskService(repository);
        RepositoryTask task = service.create("Unsafe shortcut", "", "main", RiskLevel.MEDIUM, "developer");

        assertThrows(IllegalStateException.class,
                () -> service.transition(task.id(), RepositoryTaskStatus.COMPLETED, "developer"));
    }
}
