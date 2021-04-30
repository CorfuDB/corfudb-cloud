#!/bin/bash

cd tests

# Clean previous state
./gradlew clean shutdown

# Deploy a new cluster, run tests and shutdown the cluster
./gradlew clean deployment test -Dtags=bat \

# Clean current state
./gradlew shutdown
