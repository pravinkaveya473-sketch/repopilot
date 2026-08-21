#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/build/libs/repopilot.jar"

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR. Run ./gradlew shadowJar first."
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git -C "$TMP" init -q
git -C "$TMP" config user.name RepoPilot-Test
git -C "$TMP" config user.email repopilot@example.invalid
printf 'class App {}\n' > "$TMP/App.java"
git -C "$TMP" add App.java
git -C "$TMP" commit -qm init

java -jar "$JAR" --version
java -jar "$JAR" doctor --repo "$TMP"
java -jar "$JAR" inspect --repo "$TMP"

CREATE="$(java -jar "$JAR" task create --repo "$TMP" --title "Release verification" --risk HIGH --actor developer)"
ID="$(printf '%s\n' "$CREATE" | awk -F= '/^id=/{print $2}')"
[[ -n "$ID" ]]

java -jar "$JAR" task plan --repo "$TMP" --id "$ID" --actor developer >/dev/null
java -jar "$JAR" task approve --repo "$TMP" --id "$ID" --actor tech-lead >/dev/null
java -jar "$JAR" task start --repo "$TMP" --id "$ID" --actor developer >/dev/null
java -jar "$JAR" task review --repo "$TMP" --id "$ID" --actor developer >/dev/null
java -jar "$JAR" task complete --repo "$TMP" --id "$ID" --actor reviewer >/dev/null

java -jar "$JAR" task show --repo "$TMP" --id "$ID" | grep 'status=COMPLETED'
[[ -f "$TMP/.repopilot/audit/task-audit.jsonl" ]]

echo 'class App { synchronized void execute() {} }' > "$TMP/App.java"
java -jar "$JAR" risk --repo "$TMP"
java -jar "$JAR" policy path --repo "$TMP" --value "src/main/java/App.java"
java -jar "$JAR" report --repo "$TMP" --output "$TMP/evidence.md"
[[ -f "$TMP/evidence.md" ]]

echo "REPOPILOT_V2_VERIFIED"
