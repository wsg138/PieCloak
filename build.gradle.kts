import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.compile.JavaCompile

subprojects {
    pluginManager.withPlugin("java") {
        apply(plugin = "pmd")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(21)
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
