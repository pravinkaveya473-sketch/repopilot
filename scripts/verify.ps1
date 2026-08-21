$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Jar = Join-Path $Root "build\libs\repopilot.jar"
if (-not (Test-Path $Jar)) {
    throw "Missing $Jar. Run .\gradlew.bat shadowJar first."
}
$Java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
if (-not (Test-Path $Java) -and $Java -ne "java") {
    throw "JAVA_HOME does not contain bin\java.exe: $env:JAVA_HOME"
}

$TempRepo = Join-Path $env:TEMP ("repopilot-smoke-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $TempRepo | Out-Null
try {
    git -C $TempRepo init -q
    git -C $TempRepo config user.name "RepoPilot-Test"
    git -C $TempRepo config user.email "repopilot@example.invalid"
    "class App {}" | Set-Content (Join-Path $TempRepo "App.java") -Encoding UTF8
    git -C $TempRepo add App.java
    git -C $TempRepo commit -qm init

    & $Java -jar $Jar --version
    & $Java -jar $Jar doctor --repo $TempRepo
    & $Java -jar $Jar inspect --repo $TempRepo

    $Create = & $Java -jar $Jar task create --repo $TempRepo --title "Release verification" --risk HIGH --actor developer
    $IdLine = $Create | Where-Object { $_ -like "id=*" } | Select-Object -First 1
    $TaskId = $IdLine.Substring(3)

    foreach ($Action in @("plan", "approve", "start", "review", "complete")) {
        & $Java -jar $Jar task $Action --repo $TempRepo --id $TaskId --actor reviewer | Out-Null
    }

    $Show = & $Java -jar $Jar task show --repo $TempRepo --id $TaskId
    if (-not ($Show -contains "status=COMPLETED")) {
        throw "Task workflow did not reach COMPLETED"
    }
    if (-not (Test-Path (Join-Path $TempRepo ".repopilot\audit\task-audit.jsonl"))) {
        throw "Task audit evidence was not created"
    }

    "class App { synchronized void execute() {} }" | Set-Content (Join-Path $TempRepo "App.java") -Encoding UTF8
    & $Java -jar $Jar risk --repo $TempRepo
    & $Java -jar $Jar policy path --repo $TempRepo --value "src/main/java/App.java"
    & $Java -jar $Jar report --repo $TempRepo --output (Join-Path $TempRepo "evidence.md")
    if (-not (Test-Path (Join-Path $TempRepo "evidence.md"))) {
        throw "Evidence report was not created"
    }
    Write-Host "REPOPILOT_V2_VERIFIED" -ForegroundColor Green
}
finally {
    Remove-Item -Recurse -Force $TempRepo -ErrorAction SilentlyContinue
}
