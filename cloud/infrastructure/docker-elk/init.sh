#!/usr/bin/env bash

#download docker-elk repo into a build directory
rm -rf build/docker-elk/

git clone https://github.com/deviantony/docker-elk.git build/docker-elk

docker volume rm docker-elk_elasticsearch

#copy configs into a build dir
cp -v docker-compose.yml build/docker-elk
cp -v .env build/docker-elk
cp -av elasticsearch build/docker-elk
cp -av kibana build/docker-elk

mkdir build/docker-elk/data
