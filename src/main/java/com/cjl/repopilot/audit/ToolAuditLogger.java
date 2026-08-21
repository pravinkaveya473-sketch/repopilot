package com.cjl.repopilot.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Append-only JSONL evidence log for tool permission and execution events.
 * Sensitive values and file contents are never persisted verbatim.
 */
public final class ToolAuditLogger {

    private final Path auditFile;
    private final Object monitor = new Object();

    public ToolAuditLogger(Path repositoryRoot) {
        Path root = repositoryRoot == null
                ? Path.of(System.getProperty("user.dir"))
                : repositoryRoot;
        this.auditFile = root.toAbsolutePath().normalize()
                .resolve(".repopilot")
                .resolve("audit")
                .resolve("tool-audit.jsonl");
    }

    public Path getAuditFile() {
        return auditFile;
    }

    public void log(
            String toolId,
            String toolName,
            String phase,
            String decision,
            String reason,
            Map<String, Object> args,
            boolean success,
            long durationMillis,
            String output
    ) {
        String event = "{" +
                jsonField("timestamp", Instant.now().toString()) + "," +
                jsonField("toolId", nullToEmpty(toolId)) + "," +
                jsonField("toolName", nullToEmpty(toolName)) + "," +
                jsonField("phase", nullToEmpty(phase)) + "," +
                jsonField("decision", nullToEmpty(decision)) + "," +
                jsonField("reason", truncate(nullToEmpty(reason), 500)) + "," +
                jsonField("arguments", summarizeArgs(args)) + "," +
                "\"success\":" + success + "," +
                "\"durationMillis\":" + Math.max(0, durationMillis) + "," +
                jsonField("outputSha256", sha256(nullToEmpty(output))) + "," +
                "\"outputLength\":" + nullToEmpty(output).length() +
                "}" + System.lineSeparator();

        synchronized (monitor) {
            try {
                Files.createDirectories(auditFile.getParent());
                Files.writeString(auditFile, event, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Audit logging is best-effort: a disk issue must not corrupt tool execution.
            }
        }
    }

    private static String summarizeArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        var sorted = new TreeMap<String, Object>(args);
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : sorted.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            String key = entry.getKey();
            String lower = key.toLowerCase();
            String value;
            if (lower.contains("password") || lower.contains("secret") || lower.contains("token")
                    || lower.contains("api_key") || lower.contains("apikey")) {
                value = "<redacted>";
            } else if ("content".equals(lower) || "new_string".equals(lower) || "old_string".equals(lower)) {
                int length = String.valueOf(entry.getValue()).length();
                value = "<content:" + length + " chars>";
            } else {
                value = truncate(String.valueOf(entry.getValue()), 300);
            }
            sb.append('"').append(escape(key)).append("\":\"").append(escape(value)).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonField(String key, String value) {
        return "\"" + escape(key) + "\":\"" + escape(value) + "\"";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return "unavailable";
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return nullToEmpty(value);
        return value.substring(0, max) + "...";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        return nullToEmpty(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
