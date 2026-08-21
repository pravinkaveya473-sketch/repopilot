# RepoPilot Java：5 分钟演示

## 目标

用一个真实 Git 仓库展示“建立基线 → 识别风险 → 策略决策 → 人工审批任务 → 输出证据报告”的闭环。全部操作在本地完成，不需要 API Key 或外部服务。

## 1. 构建与工程验证（45 秒）

```powershell
.\gradlew.bat clean check shadowJar
java -jar build\libs\repopilot.jar version
```

说明 shaded JAR 可直接交付，CI 执行相同验证链路。

## 2. 仓库画像（45 秒）

```powershell
java -jar build\libs\repopilot.jar doctor --repo .
java -jar build\libs\repopilot.jar inspect --repo .
```

展示当前分支/提交、工作区状态、源码与测试数量、语言、构建系统，以及 CI、许可证、安全策略等工程信号。

## 3. 安全策略（60 秒）

```powershell
java -jar build\libs\repopilot.jar policy command --value "git push --force origin main"
java -jar build\libs\repopilot.jar policy path --repo . --value ".github/workflows/release.yml"
java -jar build\libs\repopilot.jar policy path --repo . --value "src/main/java/App.java"
```

对比 `BLOCK / REQUIRE_APPROVAL / ALLOW`，强调策略用于任何 AI Agent 或自动化执行器之前的 fail-closed 边界。

## 4. 任务状态机（90 秒）

```powershell
java -jar build\libs\repopilot.jar task create --repo . --title "Review authentication migration" --risk HIGH --actor developer
java -jar build\libs\repopilot.jar task plan --repo . --id <id> --actor developer
java -jar build\libs\repopilot.jar task approve --repo . --id <id> --actor tech-lead
java -jar build\libs\repopilot.jar task start --repo . --id <id> --actor developer
java -jar build\libs\repopilot.jar task review --repo . --id <id> --actor developer
java -jar build\libs\repopilot.jar task complete --repo . --id <id> --actor reviewer
```

状态不能跳跃，任务数据采用文件锁和原子替换，生命周期证据追加到 `.repopilot/audit/task-audit.jsonl`。

## 5. 风险与证据（60 秒）

```powershell
java -jar build\libs\repopilot.jar risk --repo .
java -jar build\libs\repopilot.jar report --repo . --output evidence-report.md
```

打开报告展示仓库基线、Diff 风险因子、受治理任务和人工复核清单。

## 面试回答重点

- 风险分数是评审信号，不替代代码审查或身份认证。
- Worktree 干净不代表变更安全，因此基线、路径策略、命令策略和业务审批需要组合。
- `.repopilot` 默认不进入 Git，避免把本地路径和审计上下文意外公开。
- V2 只依赖 Jackson，不上传源码、不启用遥测，便于放入现有开发流程。
