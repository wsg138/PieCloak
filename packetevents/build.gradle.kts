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
    implementation(project(":locatables"))
    implementation(project(":logging"))
    implementation(project(":core"))

    compileOnly(project(":leafpile"))

    compileOnly("com.github.retrooper:packetevents-api:2.12.0")
    compileOnly("org.spongepowered:configurate-core:4.2.0")
    compileOnly("it.unimi.dsi:fastutil:8.5.18")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":leafpile"))
    testImplementation("com.github.retrooper:packetevents-api:2.12.0")
    testImplementation("net.kyori:adventure-api:4.25.0")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}
