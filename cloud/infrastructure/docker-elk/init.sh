#!/usr/bin/env bash

set -ex

#download docker-elk repo into a build directory
rm -rf build/docker-elk/

git clone https://github.com/deviantony/docker-elk.git build/docker-elk

cd build/docker-elk
git checkout 1bee7e1c097d3c2f9d72995d5ca2d6051f29381b
cd ../../

# do not delete elastic volume
docker volume rm docker-elk_elasticsearch || true

#copy configs into a build dir
cp -v docker-compose.yml build/docker-elk
cp -v .env build/docker-elk
cp -av elasticsearch build/docker-elk
cp -av kibana build/docker-elk

mkdir build/docker-elk/data
