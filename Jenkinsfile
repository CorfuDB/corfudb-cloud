pipeline {
    agent any

    stages {
        stage('Stateful Test') {
            steps {
                sh '.ci/stateful_tests.sh'
            }
        }
    }
}
