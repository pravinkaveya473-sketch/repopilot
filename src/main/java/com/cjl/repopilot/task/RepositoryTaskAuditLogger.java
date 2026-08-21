package com.cjl.repopilot.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Append-only lifecycle audit for repository tasks. */
public final class RepositoryTaskAuditLogger {

    private final Path auditFile;
    private final Object monitor = new Object();

    public RepositoryTaskAuditLogger(Path repositoryRoot) {
        this.auditFile = repositoryRoot.toAbsolutePath().normalize()
                .resolve(".repopilot").resolve("audit").resolve("task-audit.jsonl");
    }

    public void log(String taskId, String action, RepositoryTaskStatus from,
                    RepositoryTaskStatus to, String actor, long version) {
        String line = "{" +
                field("timestamp", Instant.now().toString()) + "," +
                field("taskId", taskId) + "," +
                field("action", action) + "," +
                field("fromStatus", from == null ? "" : from.name()) + "," +
                field("toStatus", to == null ? "" : to.name()) + "," +
                field("actor", actor == null ? "" : actor) + "," +
                "\"version\":" + version +
                "}" + System.lineSeparator();
        synchronized (monitor) {
            try {
                Files.createDirectories(auditFile.getParent());
                Files.writeString(auditFile, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Best-effort evidence logging; persistence errors are surfaced by the task store itself.
            }
        }
    }

    public Path getAuditFile() {
        return auditFile;
    }

    private static String field(String key, String value) {
        return "\"" + escape(key) + "\":\"" + escape(value == null ? "" : value) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
