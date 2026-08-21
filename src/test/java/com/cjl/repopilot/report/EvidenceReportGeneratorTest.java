package com.cjl.repopilot.report;

import com.cjl.repopilot.security.RiskLevel;
import com.cjl.repopilot.task.RepositoryTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceReportGeneratorTest {

    @Test
    void producesReviewReadyMarkdown(@TempDir Path root) throws Exception {
        run(root, "git", "init", "-b", "main");
        run(root, "git", "config", "user.email", "test@example.com");
        run(root, "git", "config", "user.name", "RepoPilot Test");
        Files.writeString(root.resolve("README.md"), "# Sample", StandardCharsets.UTF_8);
        run(root, "git", "add", "README.md");
        run(root, "git", "commit", "-m", "initial");
        new RepositoryTaskService(root).create("Review dependency update", "Validate the build",
                "main", RiskLevel.HIGH, "developer");

        Path report = new EvidenceReportGenerator().generate(root, root.resolve("evidence.md"));
        String markdown = Files.readString(report);

        assertTrue(markdown.contains("Repository baseline"));
        assertTrue(markdown.contains("Diff risk assessment"));
        assertTrue(markdown.contains("Review dependency update"));
        assertTrue(markdown.contains("Review checklist"));
    }

    private void run(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.exitValue() == 0, output);
    }
}
