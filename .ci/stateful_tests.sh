#!/bin/bash

set -e

./gradlew clean \
  deployment \
  test -Dtags=stateful \
  shutdown

