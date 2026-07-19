plugins {
    id("java")
}

group = "games.cubi.raycastedantiesp.packetevents"
version = "unspecified"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.codemc.io/repository/maven-releases/") }
    maven { url = uri("https://repo.codemc.io/repository/maven-snapshots/") }
}

dependencies {
    implementation(project(":locatable-lib"))
    implementation(project(":logging"))
    implementation(project(":core"))

    compileOnly("com.github.retrooper:packetevents-api:2.12.0")
    compileOnly("org.spongepowered:configurate-core:4.2.0")
    testImplementation("com.github.retrooper:packetevents-api:2.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    compileOnly("it.unimi.dsi:fastutil:8.5.18")
}

tasks.test { useJUnitPlatform() }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(26)
}
