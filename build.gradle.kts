plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.caoccao.javet:javet-macos:2.2.3")
    testImplementation("org.eclipse.jetty.websocket:websocket-server:9.4.49.v20220914")
    testImplementation("org.eclipse.jetty.websocket:javax-websocket-server-impl:9.4.49.v20220914")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.14.0-rc2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.1")
    testImplementation("net.bytebuddy:byte-buddy:1.12.17")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}