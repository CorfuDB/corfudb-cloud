#!/usr/bin/env bash

#download docker-elk repo into a build directory
rm -rf build/docker-elk/

git clone https://github.com/deviantony/docker-elk.git build/docker-elk

docker volume rm docker-elk_elasticsearch

#copy configs into a build dir
cp docker-compose.yml build/docker-elk
cp .env build/docker-elk

mkdir build/docker-elk/data
