plugins {
    idea
}

val gradleScriptsDir: String = project.rootDir.parentFile.parent
apply(from="${gradleScriptsDir}/gradle/idea-project.gradle.kts")
