package com.cjl.repopilot.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolAuditLoggerTest {

    @Test
    void redactsSensitiveArgumentsAndFileContent(@TempDir Path root) throws Exception {
        ToolAuditLogger logger = new ToolAuditLogger(root);
        logger.log("tool-1", "WriteFile", "execution", "SUCCESS", "",
                Map.of("file_path", "src/App.java", "content", "very-secret-source", "api_key", "sk-secret"),
                true, 10, "ok");

        String line = Files.readString(logger.getAuditFile());
        assertTrue(line.contains("<redacted>"));
        assertTrue(line.contains("<content:18 chars>"));
        assertFalse(line.contains("sk-secret"));
        assertFalse(line.contains("very-secret-source"));
    }
}
