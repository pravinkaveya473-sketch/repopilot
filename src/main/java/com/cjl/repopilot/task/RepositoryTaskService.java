package com.cjl.repopilot.task;

import com.cjl.repopilot.security.RiskLevel;

import java.nio.file.Path;
import java.util.List;

public final class RepositoryTaskService {

    private final Path repositoryRoot;
    private final RepositoryTaskStore store;
    private final RepositoryTaskAuditLogger auditLogger;

    public RepositoryTaskService(Path repositoryRoot) {
        this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
        this.store = new RepositoryTaskStore(this.repositoryRoot);
        this.auditLogger = new RepositoryTaskAuditLogger(this.repositoryRoot);
    }

    public RepositoryTask create(String title, String description, String baseBranch,
                                 RiskLevel riskLevel, String actor) {
        RepositoryTask task = RepositoryTask.create(
                title, description, repositoryRoot.toString(), baseBranch, riskLevel, actor);
        RepositoryTask persisted = store.insert(task);
        auditLogger.log(task.id(), "CREATE", null, task.status(), actor, task.version());
        return persisted;
    }

    public RepositoryTask transition(String id, RepositoryTaskStatus next, String actor) {
        RepositoryTask current = getRequired(id);
        RepositoryTask updated = current.transition(next, actor);
        RepositoryTask persisted = store.save(updated);
        auditLogger.log(id, "TRANSITION", current.status(), updated.status(), actor, updated.version());
        return persisted;
    }

    public RepositoryTask getRequired(String id) {
        return store.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository task not found: " + id));
    }

    public List<RepositoryTask> list() {
        return store.findAll();
    }
}
