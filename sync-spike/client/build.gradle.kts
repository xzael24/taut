plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("com.google.protobuf") version "0.10.0"
    application
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // gRPC
    implementation("io.grpc:grpc-netty-shaded:1.62.2")
    implementation("io.grpc:grpc-protobuf:1.62.2")
    implementation("io.grpc:grpc-stub:1.62.2")

    // Protobuf
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("com.google.protobuf:protobuf-kotlin:3.25.5")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Required for generated code
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc") {
                    option("lite")
                }
            }
        }
    }
}

application {
    mainClass.set("taut.sync.spike.client.MainKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

sourceSets {
    main {
        proto {
            srcDir("../proto")
        }
    }
}
