plugins {
    java
    application
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.pravinkaveya473sketch"
version = "2.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.cjl.repopilot.RepoPilotApplication"
}

repositories {
    mavenCentral()
}

dependencies {
    // JSON persistence for repository-local task state.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
    reports.html.required = true
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Runs RepoPilot against the current repository without modifying tracked files"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args("inspect", "--repo", project.projectDir.absolutePath)
}

tasks.named("check") { dependsOn("smokeTest") }

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "RepoPilot Java"
        attributes["Implementation-Version"] = project.version
        attributes["Implementation-Vendor"] = "pravinkaveya473-sketch"
    }
}

tasks.shadowJar {
    archiveBaseName = "repopilot"
    archiveClassifier = ""
    archiveVersion = ""
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "RepoPilot Java"
        attributes["Implementation-Version"] = project.version
        attributes["Implementation-Vendor"] = "pravinkaveya473-sketch"
    }
}

tasks.distZip { dependsOn(tasks.shadowJar) }
tasks.distTar { dependsOn(tasks.shadowJar) }
tasks.startScripts { dependsOn(tasks.shadowJar) }
tasks.named("startShadowScripts") { dependsOn(tasks.jar) }
