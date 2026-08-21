package com.cjl.repopilot.inspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryInspectorTest {

    @Test
    void detectsLanguagesBuildSystemsAndEngineeringSignals(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src/main/java"));
        Files.createDirectories(root.resolve("src/test/java"));
        Files.createDirectories(root.resolve(".github/workflows"));
        Files.writeString(root.resolve("src/main/java/App.java"), "class App {}");
        Files.writeString(root.resolve("src/test/java/AppTest.java"), "class AppTest {}");
        Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }");
        Files.writeString(root.resolve("README.md"), "# Demo");
        Files.writeString(root.resolve("LICENSE"), "MIT");

        RepositorySnapshot snapshot = new RepositoryInspector().inspect(root);

        assertEquals(2, snapshot.languages().get("Java"));
        assertTrue(snapshot.buildSystems().contains("Gradle"));
        assertTrue(snapshot.signals().contains("CI workflow"));
        assertTrue(snapshot.signals().contains("license"));
        assertEquals(2, snapshot.sourceFiles());
        assertEquals(1, snapshot.testFiles());
    }

    @Test
    void excludesGeneratedAndRuntimeDirectories(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("build/generated"));
        Files.createDirectories(root.resolve(".repopilot/audit"));
        Files.writeString(root.resolve("build/generated/Generated.java"), "class Generated {}");
        Files.writeString(root.resolve(".repopilot/audit/events.jsonl"), "{}");
        Files.writeString(root.resolve("Main.java"), "class Main {}");

        RepositorySnapshot snapshot = new RepositoryInspector().inspect(root);

        assertEquals(1, snapshot.trackedFiles());
        assertEquals(1, snapshot.languages().get("Java"));
    }
}
