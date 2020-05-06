#!/bin/sh

docker volume create log-aggregation-data

docker run -ti --rm --privileged \
  -p 8080:8080 \
  -v "$(pwd)"/config.json:/app/config.json \
  -v log-aggregation-data:/data \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$(command -v docker)":/bin/docker \
  corfudb/integration-tools:latest \
  bin/integration-tools.sh server

docker volume rm log-aggregation-data