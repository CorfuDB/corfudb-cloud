#!/bin/sh

cd build/docker-elk

docker-compose -f docker-compose.yml -f extensions/apm-server/apm-server-compose.yml up -d apm-server
