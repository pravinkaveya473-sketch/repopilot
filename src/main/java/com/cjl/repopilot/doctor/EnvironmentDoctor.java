package com.cjl.repopilot.doctor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class EnvironmentDoctor {

    public record Check(String name, boolean passed, String detail) {}

    public List<Check> inspect(Path repositoryRoot) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        var checks = new ArrayList<Check>();

        int javaFeature = Runtime.version().feature();
        checks.add(new Check("Java 21+", javaFeature >= 21,
                "Detected Java " + Runtime.version()));
        checks.add(checkCommand("Git", List.of("git", "--version")));
        checks.add(new Check("Repository directory", Files.isDirectory(root), root.toString()));
        checks.add(new Check("Git repository", Files.isDirectory(root.resolve(".git")),
                Files.isDirectory(root.resolve(".git")) ? "Git metadata found" : "No .git directory found"));

        Path runtimeDir = root.resolve(".repopilot");
        try {
            Files.createDirectories(runtimeDir);
            Path probe = Files.createTempFile(runtimeDir, "write-probe-", ".tmp");
            Files.deleteIfExists(probe);
            checks.add(new Check("RepoPilot runtime directory", true, runtimeDir.toString()));
        } catch (IOException ex) {
            checks.add(new Check("RepoPilot runtime directory", false, ex.getMessage()));
        }

        checks.add(new Check("Working tree readable", Files.isReadable(root),
                Files.isReadable(root) ? "Repository files are readable" : "Repository cannot be inspected"));

        return checks;
    }

    public boolean allRequiredPassed(List<Check> checks) {
        return checks.stream()
                .filter(check -> !"Git repository".equals(check.name()))
                .allMatch(Check::passed);
    }

    private Check checkCommand(String name, List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new Check(name, false, "Command timed out");
            }
            String output = new String(process.getInputStream().readAllBytes()).strip();
            return new Check(name, process.exitValue() == 0,
                    output.isBlank() ? "exit=" + process.exitValue() : output.lines().findFirst().orElse(output));
        } catch (Exception ex) {
            return new Check(name, false, ex.getMessage());
        }
    }
}
