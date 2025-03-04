plugins {
    kotlin("jvm") version "2.1.0"
}


repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

tasks.register("buildFrontend",Exec::class){
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
