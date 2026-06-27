plugins {
    kotlin("jvm") version "2.1.20"
    application
}

group = "com.spike"
version = "1.0.0"

application {
    mainClass.set("CryptoSpikeKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.code.gson:gson:2.11.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "CryptoSpikeKt"
        )
    }
}
