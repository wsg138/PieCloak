import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.compile.JavaCompile

val javaVersionProvider = providers.gradleProperty("javaVersion").map { it.toInt() }
val minecraftVersionProvider = providers.gradleProperty("minecraftVersion")
val paperDevBundleVersionProvider = providers.gradleProperty("paperDevBundleVersion")

check(javaVersionProvider.get() == 21) {
    "The current 1.21.11 production baseline requires Java 21."
}
check(minecraftVersionProvider.get() == "1.21.11") {
    "The current production target must remain Minecraft 1.21.11 until an intentional upgrade package changes it."
}
check(paperDevBundleVersionProvider.get() == "1.21.11-R0.1-SNAPSHOT") {
    "The current Paper development bundle must remain 1.21.11-R0.1-SNAPSHOT."
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
