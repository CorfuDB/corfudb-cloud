#!/bin/bash

./gradlew clean \
  deployment \
  test -Dtags=stateful \
  shutdown

