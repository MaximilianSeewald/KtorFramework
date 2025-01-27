plugins {
    kotlin("jvm") version "1.9.23"
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
