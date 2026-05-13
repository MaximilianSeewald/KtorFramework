plugins {
    kotlin("jvm") version "2.2.20"
    id("io.ktor.plugin") version "3.4.3"
    kotlin("plugin.serialization") version "2.2.20"
    application
}

group = "com.loudless"
version = "1.0-SNAPSHOT"

val ktorVersion = "3.4.3"
val exposedVersion = "0.59.0"
val h2Version = "2.3.232"
val hikariVersion = "6.2.1"
val logbackVersion = "1.5.32"

repositories {
    mavenCentral()
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin")
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-openapi:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")
    implementation("io.ktor:ktor-server-routing-openapi:${ktorVersion}")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")

    implementation("com.h2database:h2:$h2Version")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.jsoizo:kotlin-csv-jvm:1.10.0")
    implementation("at.favre.lib:bcrypt:0.10.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("backupDatabase") {
    group = "application"
    description = "Create a timestamped H2 database backup using the runtime database configuration."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.loudless.database.DatabaseBackupCommandKt")
    systemProperties(System.getProperties().entries.associate { it.key.toString() to it.value })
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
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}
