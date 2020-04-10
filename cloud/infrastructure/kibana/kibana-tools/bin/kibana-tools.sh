#!/bin/sh

java -cp "kibana-tools.jar:/app/lib/*" org.corfudb.cloud.infrastructure.kibana.tools.MainKt "$@"
