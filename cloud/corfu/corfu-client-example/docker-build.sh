#!/usr/bin/env bash

set -e

./gradlew clean jar

docker build -t corfudb/corfu-client-example:latest .