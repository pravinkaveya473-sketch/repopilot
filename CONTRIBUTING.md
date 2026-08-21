# Contributing

1. Create a focused branch from `main`.
2. Keep generated `.repopilot/` evidence and credentials out of Git.
3. Add tests for every policy rule, state transition or risk-scoring change.
4. Run `./gradlew clean check shadowJar` before opening a pull request.
5. Document user-visible changes in `CHANGELOG.md`.

Security controls should fail closed: an ambiguous destructive command is blocked or requires explicit approval. Avoid adding network calls or telemetry to the core CLI.
