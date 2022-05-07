## Corfu Cloud Native Design

- Dockerized corfu
  - build: [maven](https://github.com/CorfuDB/CorfuDB/blob/0d8b8f41b312c5e95b474ba10334294ba927cee3/infrastructure/pom.xml#L24)
  - Dockerfile: [source code](https://github.com/CorfuDB/CorfuDB/blob/master/infrastructure/Dockerfile)
  - deployment: [github actions](https://github.com/CorfuDB/CorfuDB/blob/0d8b8f41b312c5e95b474ba10334294ba927cee3/.github/workflows/publish-corfu.yml#L49)
  - docker hub: [link](https://hub.docker.com/repository/docker/corfudb/corfu-server)

- Cloud native corfu:
  - run kubernetes locally: [k3d embedded kube cluster](https://k3d.io/)
  - [helm chart](corfu)
  - helm chart deployment: [github actions](https://github.com/CorfuDB/corfudb-cloud/blob/master/.github/workflows/run-corfu-cloud-deployment.yml)
    