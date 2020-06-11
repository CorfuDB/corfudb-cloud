#!/usr/bin/env bash
set -e

cd build/docker-elk

docker-compose down
docker image rm docker-elk_kibana
docker image rm docker-elk_elasticsearch
