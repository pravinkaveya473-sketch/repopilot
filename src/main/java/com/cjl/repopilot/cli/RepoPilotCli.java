package com.cjl.repopilot.cli;

import com.cjl.repopilot.doctor.EnvironmentDoctor;
import com.cjl.repopilot.inspect.RepositoryInspector;
import com.cjl.repopilot.inspect.RepositorySnapshot;
import com.cjl.repopilot.report.EvidenceReportGenerator;
import com.cjl.repopilot.risk.DiffRiskAnalyzer;
import com.cjl.repopilot.risk.DiffRiskReport;
import com.cjl.repopilot.security.EnterpriseCommandPolicy;
import com.cjl.repopilot.security.PolicyAssessment;
import com.cjl.repopilot.security.RepositoryPathPolicy;
import com.cjl.repopilot.security.RiskLevel;
import com.cjl.repopilot.task.RepositoryTask;
import com.cjl.repopilot.task.RepositoryTaskService;
import com.cjl.repopilot.task.RepositoryTaskStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RepoPilotCli {

    public int execute(String[] args) {
        if (args.length == 0) return printHelp();
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "--doctor", "doctor" -> runDoctor(parseOptions(args, 1));
                case "--risk-report", "risk" -> runRisk(parseOptions(args, 1));
                case "inspect" -> runInspect(parseOptions(args, 1));
                case "report" -> runReport(parseOptions(args, 1));
                case "policy" -> runPolicy(Arrays.copyOfRange(args, 1, args.length));
                case "task" -> runTask(Arrays.copyOfRange(args, 1, args.length));
                case "--version", "version" -> printVersion();
                case "--help", "help" -> printHelp();
                default -> { System.err.println("Unknown command: " + args[0]); yield 2; }
            };
        } catch (Exception ex) {
            System.err.println("RepoPilot error: " + ex.getMessage());
            return 1;
        }
    }

    private int runDoctor(Map<String, String> options) {
        Path repo = repositoryPath(options);
        var doctor = new EnvironmentDoctor();
        var checks = doctor.inspect(repo);
        System.out.println("RepoPilot environment doctor");
        System.out.println("Repository: " + repo);
        for (var check : checks) {
            System.out.printf("[%s] %-28s %s%n", check.passed() ? "PASS" : "FAIL", check.name(), check.detail());
        }
        return doctor.allRequiredPassed(checks) ? 0 : 1;
    }

    private int runInspect(Map<String, String> options) throws Exception {
        RepositorySnapshot snapshot = new RepositoryInspector().inspect(repositoryPath(options));
        System.out.println("RepoPilot repository snapshot");
        System.out.println("Repository: " + snapshot.repository());
        System.out.println("Branch/commit: " + snapshot.branch() + " @ " + snapshot.headCommit());
        System.out.println("Working tree: " + (snapshot.dirty() ? "DIRTY" : "CLEAN"));
        System.out.printf("Files: %d total, %d source, %d tests, %.2f MiB%n",
                snapshot.trackedFiles(), snapshot.sourceFiles(), snapshot.testFiles(), snapshot.totalBytes() / 1024d / 1024d);
        System.out.println("Languages: " + snapshot.languages());
        System.out.println("Build systems: " + snapshot.buildSystems());
        System.out.println("Engineering signals: " + snapshot.signals());
        return 0;
    }

    private int runRisk(Map<String, String> options) throws Exception {
        Path repo = repositoryPath(options);
        String diffFile = options.get("diff-file");
        DiffRiskReport report = diffFile == null
                ? new DiffRiskAnalyzer().analyzeRepository(repo)
                : new DiffRiskAnalyzer().analyze(Files.readString(Path.of(diffFile)));
        printRisk(report);
        return report.riskLevel() == RiskLevel.CRITICAL ? 3 : report.riskLevel() == RiskLevel.HIGH ? 2 : 0;
    }

    private int runReport(Map<String, String> options) throws Exception {
        Path repo = repositoryPath(options);
        Path output = options.containsKey("output") ? Path.of(options.get("output"))
                : repo.resolve(".repopilot/reports/evidence-report.md");
        Path written = new EvidenceReportGenerator().generate(repo, output);
        System.out.println("Evidence report written: " + written);
        return 0;
    }

    private int runPolicy(String[] args) {
        if (args.length == 0) { printPolicyHelp(); return 2; }
        Map<String, String> options = parseOptions(args, 1);
        Path repo = repositoryPath(options);
        PolicyAssessment assessment = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "command" -> EnterpriseCommandPolicy.assess(require(options, "value"));
            case "path" -> RepositoryPathPolicy.assessWrite(repo, require(options, "value"));
            default -> throw new IllegalArgumentException("Use policy command|path --value <text>");
        };
        System.out.println("decision=" + assessment.decision());
        System.out.println("risk=" + assessment.riskLevel());
        System.out.println("category=" + assessment.category());
        System.out.println("reason=" + assessment.reason());
        return switch (assessment.decision()) { case ALLOW -> 0; case REQUIRE_APPROVAL -> 2; case BLOCK -> 3; };
    }

    private int runTask(String[] args) {
        if (args.length == 0) { printTaskHelp(); return 2; }
        String action = args[0].toLowerCase(Locale.ROOT);
        Map<String, String> options = parseOptions(args, 1);
        RepositoryTaskService service = new RepositoryTaskService(repositoryPath(options));
        String actor = options.getOrDefault("actor", System.getProperty("user.name", "local-user"));
        return switch (action) {
            case "create" -> {
                RepositoryTask task = service.create(require(options, "title"), options.getOrDefault("description", ""),
                        options.getOrDefault("base-branch", "main"),
                        RiskLevel.valueOf(options.getOrDefault("risk", "MEDIUM").toUpperCase(Locale.ROOT)), actor);
                printTask(task); yield 0;
            }
            case "list" -> { var tasks = service.list(); if (tasks.isEmpty()) System.out.println("No repository tasks found."); else tasks.forEach(this::printTaskSummary); yield 0; }
            case "show" -> { printTask(service.getRequired(require(options, "id"))); yield 0; }
            case "plan" -> transition(service, options, RepositoryTaskStatus.PLANNED, actor);
            case "approve" -> transition(service, options, RepositoryTaskStatus.APPROVED, actor);
            case "start" -> transition(service, options, RepositoryTaskStatus.IN_PROGRESS, actor);
            case "review" -> transition(service, options, RepositoryTaskStatus.REVIEW_REQUIRED, actor);
            case "complete" -> transition(service, options, RepositoryTaskStatus.COMPLETED, actor);
            case "fail" -> transition(service, options, RepositoryTaskStatus.FAILED, actor);
            case "cancel" -> transition(service, options, RepositoryTaskStatus.CANCELLED, actor);
            default -> { printTaskHelp(); yield 2; }
        };
    }

    private int transition(RepositoryTaskService service, Map<String, String> options, RepositoryTaskStatus status, String actor) {
        printTask(service.transition(require(options, "id"), status, actor)); return 0;
    }

    private Path repositoryPath(Map<String, String> options) {
        return Path.of(options.getOrDefault("repo", System.getProperty("user.dir"))).toAbsolutePath().normalize();
    }

    private static Map<String, String> parseOptions(String[] args, int start) {
        var result = new LinkedHashMap<String, String>();
        for (int i = start; i < args.length; i++) {
            String arg = args[i]; if (!arg.startsWith("--")) continue;
            String key = arg.substring(2), value = "true"; int eq = key.indexOf('=');
            if (eq >= 0) { value = key.substring(eq + 1); key = key.substring(0, eq); }
            else if (i + 1 < args.length && !args[i + 1].startsWith("--")) value = args[++i];
            result.put(key, value);
        }
        return result;
    }

    private static String require(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required option --" + key);
        return value;
    }

    private void printRisk(DiffRiskReport report) {
        System.out.println("RepoPilot diff risk report");
        System.out.println("Risk: " + report.riskLevel() + " (score=" + report.score() + "/100)");
        System.out.println("Files: " + report.filesChanged() + ", additions: " + report.additions() + ", deletions: " + report.deletions());
        report.factors().forEach(factor -> System.out.println("- " + factor));
    }
    private void printTaskSummary(RepositoryTask task) { System.out.printf("%s  %-16s %-8s %s%n", task.id(), task.status(), task.riskLevel(), task.title()); }
    private void printTask(RepositoryTask task) {
        System.out.println("id=" + task.id()); System.out.println("title=" + task.title()); System.out.println("status=" + task.status());
        System.out.println("risk=" + task.riskLevel()); System.out.println("repository=" + task.repositoryRoot());
        System.out.println("baseBranch=" + task.baseBranch()); System.out.println("workBranch=" + task.workBranch());
        System.out.println("createdBy=" + task.createdBy()); System.out.println("approvedBy=" + task.approvedBy());
        System.out.println("version=" + task.version()); System.out.println("updatedAt=" + task.updatedAt());
    }
    private int printVersion() { System.out.println("RepoPilot Java 2.0.0"); System.out.println("Independent repository change-governance CLI by pravinkaveya473-sketch."); return 0; }
    private int printHelp() {
        System.out.println("""
                RepoPilot Java 2.0 - repository change governance

                Commands:
                  repopilot doctor  [--repo <path>]
                  repopilot inspect [--repo <path>]
                  repopilot risk    [--repo <path>] [--diff-file <path>]
                  repopilot report  [--repo <path>] [--output <file>]
                  repopilot policy command|path --value <text> [--repo <path>]
                  repopilot task create|list|show|plan|approve|start|review|complete|fail|cancel [options]
                  repopilot version
                """); return 0;
    }
    private void printTaskHelp() { System.out.println("Use: task create|list|show|plan|approve|start|review|complete|fail|cancel [options]"); }
    private void printPolicyHelp() { System.out.println("Use: policy command|path --value <text> [--repo <path>]"); }
}
