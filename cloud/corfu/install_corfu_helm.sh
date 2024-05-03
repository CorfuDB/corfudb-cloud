#!/usr/bin/env bash

set -e

helm install corfu corfu --set tls.enabled=false --set tls.certificate.enabled=false --set global.replicas=3 --set image.repository=corfudb/corfu-server --set image.tag=0.4.2.0-SNAPSHOT