task<Exec>("docker") {
    commandLine("docker-compose", "build")
}

task<Exec>("dockerPush") {
    commandLine("docker-compose", "push")
}
