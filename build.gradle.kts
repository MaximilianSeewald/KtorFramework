plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.owasp.dependencycheck") version "12.2.1"
}

repositories {
    mavenCentral()
}

dependencyCheck {
    failBuildOnCVSS = 7.0F
    formats = listOf("HTML", "JUNIT", "SARIF")
    scanProjects = listOf(":backend")
    nvd {
        System.getenv("NVD_API_KEY")?.takeIf { it.isNotBlank() }?.let {
            apiKey = it
        }
        validForHours = 24
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.register("buildFrontend", Exec::class) {
    commandLine("cmd", "/c", "cd webapp && ng build")
}

tasks.register("deploy") {
    dependsOn("buildFrontend")
    dependsOn("backend:buildFatJar")
    doLast {
        file("backend/build/libs/fat.jar").copyTo(file("deploy/ktor.jar"), overwrite = true)
    }
}

tasks.register("deployGithub") {
    dependsOn("backend:buildFatJar")
    doLast {
        file("backend/build/libs/fat.jar").copyTo(file("deploy/ktor.jar"), overwrite = true)
    }
}

tasks.build {
    doLast {
        file("build").deleteRecursively()
    }
}
