
val idea = extensions.getByType(org.gradle.plugins.ide.idea.model.IdeaModel::class)

idea.project {
    // Set the version control system
    // to Git for this project.
    // All values IntelliJ IDEA supports
    // can be used. E.g. Subversion, Mercurial.
    vcs = "Git"

    setLanguageLevel("21")
}

idea.module {
    isDownloadJavadoc = true
    isDownloadSources = false
}