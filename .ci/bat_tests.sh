#!/bin/bash

set -e

./gradlew clean \
  deployment \
  test -Dtags=bat \
  shutdown

