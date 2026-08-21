package com.cjl.repopilot.inspect;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RepositorySnapshot(
        String repository,
        String branch,
        String headCommit,
        boolean dirty,
        long trackedFiles,
        long sourceFiles,
        long testFiles,
        long totalBytes,
        Map<String, Long> languages,
        List<String> buildSystems,
        List<String> signals,
        Instant capturedAt
) {}
