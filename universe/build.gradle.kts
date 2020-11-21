plugins {
    idea
}

idea {
    project {
        // Set the version control system
        // to Git for this project.
        // All values IntelliJ IDEA supports
        // can be used. E.g. Subversion, Mercurial.
        vcs = "Git"

        setLanguageLevel("1.8")
    }

    module {
        isDownloadJavadoc = true
        isDownloadSources = false
    }
}
