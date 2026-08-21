package com.cjl.repopilot.report;

import com.cjl.repopilot.inspect.RepositoryInspector;
import com.cjl.repopilot.risk.DiffRiskAnalyzer;
import com.cjl.repopilot.task.RepositoryTaskService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class EvidenceReportGenerator {
    public Path generate(Path repositoryRoot, Path output) throws IOException, InterruptedException {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        var snapshot = new RepositoryInspector().inspect(root);
        var risk = new DiffRiskAnalyzer().analyzeRepository(root);
        var tasks = new RepositoryTaskService(root).list();
        Path target = output.isAbsolute() ? output.normalize() : root.resolve(output).normalize();
        if (target.getParent() != null) Files.createDirectories(target.getParent());

        StringBuilder document = new StringBuilder();
        document.append("# RepoPilot Change Evidence Report\n\n")
                .append("> Generated at ").append(Instant.now()).append(" by RepoPilot Java 2.0.0.\n\n")
                .append("## Repository baseline\n\n| Field | Value |\n|---|---|\n")
                .append("| Repository | `").append(escape(snapshot.repository())).append("` |\n")
                .append("| Branch / commit | `").append(escape(snapshot.branch())).append(" @ ").append(escape(snapshot.headCommit())).append("` |\n")
                .append("| Working tree | ").append(snapshot.dirty() ? "Dirty" : "Clean").append(" |\n")
                .append("| Files | ").append(snapshot.trackedFiles()).append(" total / ").append(snapshot.sourceFiles()).append(" source / ").append(snapshot.testFiles()).append(" test |\n")
                .append("| Build systems | ").append(escape(String.join(", ", snapshot.buildSystems()))).append(" |\n")
                .append("| Engineering signals | ").append(escape(String.join(", ", snapshot.signals()))).append(" |\n\n")
                .append("## Diff risk assessment\n\n**").append(risk.riskLevel()).append(" — ").append(risk.score()).append("/100**\n\n")
                .append("Changed files: ").append(risk.filesChanged()).append(", additions: ").append(risk.additions()).append(", deletions: ").append(risk.deletions()).append(".\n\n");
        for (String factor : risk.factors()) document.append("- ").append(escape(factor)).append('\n');
        document.append("\n## Governed tasks\n\n");
        if (tasks.isEmpty()) document.append("No repository tasks recorded.\n");
        else {
            document.append("| ID | Status | Risk | Title | Owner / approver |\n|---|---|---|---|---|\n");
            tasks.forEach(task -> document.append("| `").append(task.id(), 0, Math.min(8, task.id().length())).append("` | ")
                    .append(task.status()).append(" | ").append(task.riskLevel()).append(" | ").append(escape(task.title()))
                    .append(" | ").append(escape(task.createdBy())).append(" / ").append(escape(task.approvedBy())).append(" |\n"));
        }
        document.append("\n## Review checklist\n\n- [ ] Risk factors reviewed\n- [ ] Tests and build evidence attached\n- [ ] High-risk paths approved by a second reviewer\n- [ ] Rollback approach documented\n- [ ] Audit files retained with the change record\n");
        Files.writeString(target, document.toString(), StandardCharsets.UTF_8);
        return target;
    }
    private String escape(String value) { return value == null ? "" : value.replace("|", "\\|").replace("\n", " "); }
}
