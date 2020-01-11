#!/bin/bash

./gradlew clean \
  deployment \
  test -Dtags=bat \
  shutdown

