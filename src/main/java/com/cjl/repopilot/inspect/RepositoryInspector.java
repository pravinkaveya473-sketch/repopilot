package com.cjl.repopilot.inspect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class RepositoryInspector {

    private static final Set<String> EXCLUDED = Set.of(".git", ".gradle", "build", "target", "node_modules", ".idea", ".repopilot");
    private static final Map<String, String> LANGUAGE_BY_EXTENSION = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("kts", "Kotlin"),
            Map.entry("js", "JavaScript"), Map.entry("ts", "TypeScript"), Map.entry("tsx", "TypeScript"),
            Map.entry("py", "Python"), Map.entry("go", "Go"), Map.entry("rs", "Rust"),
            Map.entry("sql", "SQL"), Map.entry("html", "HTML"), Map.entry("css", "CSS"),
            Map.entry("yaml", "YAML"), Map.entry("yml", "YAML"), Map.entry("xml", "XML"));

    public RepositorySnapshot inspect(Path repositoryRoot) throws IOException {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Repository directory does not exist: " + root);

        long files = 0, sourceFiles = 0, testFiles = 0, bytes = 0;
        Map<String, Long> languages = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(this::isIncluded).toList()) {
                files++;
                bytes += safeSize(path);
                String relative = root.relativize(path).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (relative.contains("/src/") || relative.startsWith("src/")) sourceFiles++;
                if (relative.contains("/test/") || relative.startsWith("test/") || relative.startsWith("tests/")) testFiles++;
                String language = LANGUAGE_BY_EXTENSION.get(extension(path));
                if (language != null) languages.merge(language, 1L, Long::sum);
            }
        }

        List<String> buildSystems = new ArrayList<>();
        detect(root, buildSystems, "Gradle", "gradlew", "build.gradle", "build.gradle.kts");
        detect(root, buildSystems, "Maven", "mvnw", "pom.xml");
        detect(root, buildSystems, "Node.js", "package.json");
        detect(root, buildSystems, "Docker", "Dockerfile", "compose.yaml", "docker-compose.yml");

        List<String> signals = new ArrayList<>();
        signal(root, signals, ".github/workflows", "CI workflow");
        signal(root, signals, "README.md", "README");
        signal(root, signals, "LICENSE", "license");
        signal(root, signals, "SECURITY.md", "security policy");
        signal(root, signals, "CONTRIBUTING.md", "contribution guide");

        return new RepositorySnapshot(root.toString(), git(root, "branch", "--show-current"),
                git(root, "rev-parse", "--short", "HEAD"), !git(root, "status", "--porcelain").isBlank(),
                files, sourceFiles, testFiles, bytes, languages, buildSystems, signals, java.time.Instant.now());
    }

    private boolean isIncluded(Path path) {
        for (Path part : path) if (EXCLUDED.contains(part.toString())) return false;
        return true;
    }

    private long safeSize(Path path) { try { return Files.size(path); } catch (IOException ignored) { return 0; } }
    private String extension(Path path) {
        String name = path.getFileName().toString(); int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private void detect(Path root, List<String> values, String name, String... markers) {
        for (String marker : markers) if (Files.exists(root.resolve(marker))) { values.add(name); return; }
    }
    private void signal(Path root, List<String> values, String marker, String label) {
        if (Files.exists(root.resolve(marker))) values.add(label);
    }

    private String git(Path root, String... args) {
        try {
            List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); return "unavailable"; }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return process.exitValue() == 0 ? output : "unavailable";
        } catch (Exception ignored) { return "unavailable"; }
    }
}
