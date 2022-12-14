#!/usr/bin/env bash

set -e

helm repo add litmuschaos https://litmuschaos.github.io/litmus-helm/
helm install chaos litmus --set portal.frontend.service.type=NodePort