import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile

subprojects {
    pluginManager.withPlugin("java") {
        apply(plugin = "pmd")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.toVersion(25)
            targetCompatibility = JavaVersion.toVersion(25)
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(25)
        }

        extensions.configure<PmdExtension> {
            toolVersion = "7.25.0"
            ruleSetFiles = files(rootProject.file("config/pmd/pmd-ruleset.xml"))
            ruleSets = emptyList()
            isConsoleOutput = true
            isIgnoreFailures = true
        }

        tasks.withType<Pmd>().configureEach {
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
    }
}
