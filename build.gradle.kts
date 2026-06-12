import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension

subprojects {
    pluginManager.withPlugin("java") {
        apply(plugin = "pmd")

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
