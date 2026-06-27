plugins {
    kotlin("jvm") version "2.1.20"
    id("io.ktor.plugin") version "2.3.13"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.taut"
version = "0.1.0"

val ktorVersion = "2.3.13"
val grpcVersion = "1.62.2"
val protobufVersion = "3.25.5"
val grpcKotlinVersion = "1.4.1"

apply(plugin = "io.ktor.plugin")
apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "com.google.protobuf")

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server core
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")

    // Ktor serialization
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    // Ktor auth
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")

    // Ktor status pages (error handling)
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    // Ktor CORS
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")

    // Ktor call logging
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")

    // Ktor metrics (Micrometer)
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.3")

    // Database — HikariCP + PostgreSQL (+ H2 for dev/test)
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.h2database:h2:2.2.224")

    // Database migration — Flyway
    implementation("org.flywaydb:flyway-core:10.11.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.11.0")

    // Exposed (lightweight Kotlin SQL framework — optional for future use)
    implementation("org.jetbrains.exposed:exposed-core:0.51.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.51.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.51.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.51.1")

    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // UUID support
    implementation("com.fasterxml.uuid:java-uuid-generator:5.0.0")

    // bcrypt for PIN hashing
    implementation("at.favre.lib:bcrypt:0.10.2")

    // ── gRPC ──
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")

    // Protobuf
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")

    // Required for generated gRPC code (javax.annotation)
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.10")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("../proto")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.taut.ApplicationKt")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveFileName.set("taut-backend.jar")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.taut.ApplicationKt"
    }
}
