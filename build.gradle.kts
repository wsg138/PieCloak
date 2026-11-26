import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.compile.JavaCompile

val javaVersionProvider = providers.gradleProperty("javaVersion").map { it.toInt() }
val minecraftVersionProvider = providers.gradleProperty("minecraftVersion")
val paperDevBundleVersionProvider = providers.gradleProperty("paperDevBundleVersion")

val packetEventsVersionProvider = providers.gradleProperty("packetEventsVersion")

// Only these explicitly qualified build profiles are accepted. The older
// server target remains the default and must not silently drift.
val supportedPlatform = when (minecraftVersionProvider.get()) {
    "1.21.11" -> javaVersionProvider.get() == 21 &&
        paperDevBundleVersionProvider.get() == "1.21.11-R0.1-SNAPSHOT" &&
        packetEventsVersionProvider.get() == "2.12.0"
    "26.3" -> javaVersionProvider.get() == 25 &&
        paperDevBundleVersionProvider.get() == "26.3.build.262-stable" &&
        packetEventsVersionProvider.get() == "2.14.0"
    else -> false
}
check(supportedPlatform) {
    "Unsupported platform profile: Minecraft ${minecraftVersionProvider.get()}, Java ${javaVersionProvider.get()}, Paper ${paperDevBundleVersionProvider.get()}, PacketEvents ${packetEventsVersionProvider.get()}"
}

tasks.register("verifyCurrentPlatformBaseline") {
    group = "verification"
    description = "Confirms that configuration passed the checked-in production platform baseline guard."
}

subprojects {
    pluginManager.withPlugin("java") {
        apply(plugin = "pmd")

        extensions.configure<JavaPluginExtension> {
            val javaVersion = JavaVersion.toVersion(javaVersionProvider.get())
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(javaVersionProvider)
        }

        extensions.configure<PmdExtension> {
            toolVersion = "7.25.0"
            ruleSetFiles = files(rootProject.file("config/pmd/pmd-ruleset.xml"))
            ruleSets = emptyList()
            isConsoleOutput = true
            isIgnoreFailures = true
        }

        tasks.withType<Pmd>().configureEach {
            // These rules do not need third-party type resolution. Keeping the optional
            // analysis classpath empty also avoids resolving conflicting LZ4 capabilities
            // exposed by Paperweight and LeafPile solely for PMD.
            classpath = files()

            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
    }
}

extra["patchedConfigurateYaml"] = files("libraries/configurate-yaml/yaml-4.3.0-SNAPSHOT-cubi.jar")
