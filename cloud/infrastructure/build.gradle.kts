plugins {
    idea
}

val gradleScriptsDir: String = project.rootDir.parent
apply(from="${gradleScriptsDir}/gradle/idea-project.gradle.kts")
