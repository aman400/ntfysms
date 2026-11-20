import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version "9.5.1" apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

abstract class SyncVersionTask @Inject constructor(
    private val layout: ProjectLayout
) : DefaultTask() {

    @TaskAction
    fun sync() {
        val appBuildFile = layout.projectDirectory.file("app/build.gradle.kts").asFile
        val versionNameRegex = Regex("""versionName\s*=\s*"([^"]+)"""")
        val matchResult = versionNameRegex.find(appBuildFile.readText())
        val versionName = matchResult?.groupValues?.get(1) ?: "2.0.0"

        logger.lifecycle("Syncing version '$versionName' across project documentation and release guides...")

        // 1. Update DOCUMENTATION.md
        val docFile = layout.projectDirectory.file("DOCUMENTATION.md").asFile
        if (docFile.exists()) {
            val updated = docFile.readText()
                .replace(Regex("""ZopHop-v\d+\.\d+\.\d+\.apk"""), "ZopHop-v$versionName.apk")
            docFile.writeText(updated)
            logger.lifecycle("Updated DOCUMENTATION.md -> v$versionName")
        }

        // 3. Update RELEASE_GUIDE.md
        val releaseGuideFile = layout.projectDirectory.file("RELEASE_GUIDE.md").asFile
        if (releaseGuideFile.exists()) {
            val updated = releaseGuideFile.readText()
                .replace(Regex("""<!-- APP_VERSION = "[^"]+" -->"""), "<!-- APP_VERSION = \"$versionName\" -->")
                .replace(Regex("""VERSION="[^"]+""""), "VERSION=\"$versionName\"")
                .replace(Regex("""ZopHop-v\d+\.\d+\.\d+\.apk"""), "ZopHop-v$versionName.apk")
            releaseGuideFile.writeText(updated)
            logger.lifecycle("Updated RELEASE_GUIDE.md -> VERSION=\"$versionName\"")
        }

        // 4. Update docs/index.html (updates single const APP_VERSION variable)
        val docsIndexFile = layout.projectDirectory.file("docs/index.html").asFile
        if (docsIndexFile.exists()) {
            val updated = docsIndexFile.readText()
                .replace(Regex("""const APP_VERSION = "[^"]+";"""), "const APP_VERSION = \"$versionName\";")
            docsIndexFile.writeText(updated)
            logger.lifecycle("Updated docs/index.html -> const APP_VERSION = \"$versionName\"")
        }

        logger.lifecycle("✅ Version synchronization complete!")
    }
}

tasks.register<SyncVersionTask>("syncVersion") {
    group = "versioning"
    description = "Synchronizes the app versionName across README.md, DOCUMENTATION.md, RELEASE_GUIDE.md, and docs/index.html"
}
