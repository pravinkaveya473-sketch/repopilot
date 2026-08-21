# CLI reference

| Command | Exit codes | Description |
|---|---|---|
| `doctor` | `0` ready, `1` required check failed | Validate Java, Git, repository and runtime write access |
| `inspect` | `0` success | Capture branch, commit, dirty state, file/language counts and engineering signals |
| `risk` | `0` low/medium, `2` high, `3` critical | Score current `git diff HEAD` or a supplied unified diff |
| `policy command` | `0` allow, `2` approval, `3` block | Assess a shell command without executing it |
| `policy path` | `0` allow, `2` approval, `3` block | Assess a proposed repository write |
| `task ...` | `0` success, `1` invalid operation | Manage the governed task lifecycle |
| `report` | `0` success | Generate a Markdown evidence report |

All commands accept `--repo <path>` where applicable. If omitted, the current working directory is used.

## Task state machine

```text
DRAFT → PLANNED → APPROVED → IN_PROGRESS → REVIEW_REQUIRED → COMPLETED
   └──────────────→ CANCELLED         └────→ FAILED → PLANNED
```

State is stored under `.repopilot/tasks/tasks.json`; lifecycle events are stored under `.repopilot/audit/task-audit.jsonl`.
