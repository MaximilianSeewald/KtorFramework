plugins {
    kotlin("jvm") version "1.9.23"
    id("io.ktor.plugin") version "3.0.3"
    application
}

group = "com.loudless"
version = "1.0-SNAPSHOT"

val ktorVersion = "3.0.3"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.loudless.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("fat.jar")
    }
}