#!/usr/bin/env bash

set -e

./gradlew clean jar --stacktrace

docker build -t corfudb/corfu-client-example:latest .