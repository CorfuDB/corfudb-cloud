#!/bin/bash

set -e

# Clean previous state
./gradlew -p tests clean shutdown

# Deploy a new cluster, run tests and shutdown the cluster
./gradlew -p tests \
  clean \
  deployment \
  test -Dtags=bat \
  shutdown
