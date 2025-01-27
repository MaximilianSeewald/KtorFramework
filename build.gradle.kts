plugins {
    kotlin("jvm") version "1.9.23"
    id("io.ktor.plugin") version "3.0.3"
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
    dependsOn("build")
    dependsOn("buildFrontend")
    dependsOn("backend:buildFatJar")
    doLast {
        file("backend/build/libs/fat.jar").copyTo(file("deploy/ktor.jar"), overwrite = true)
        file("build").deleteRecursively()
    }
}
