#!/bin/zsh

set -e

docker pull corfudb/corfudb-cloud-benchmarks:latest

#k3d cluster delete corfu
#docker volume rm $(docker volume ls -q) || true

#k3d cluster create corfu

#helm repo add jetstack https://charts.jetstack.io

#echo install cert manager
#helm install cert-manager jetstack/cert-manager --namespace cert-manager --create-namespace --version v1.8.0 --set installCRDs=true

#echo pause 10 sec...
#sleep 10

#echo install corfu
#helm install corfu corfu --set persistence.enabled=true --set global.replicas=3

#echo pause 30 sec...
#sleep 30

if kubectl wait --for=condition=complete --timeout=360s job/configure-corfu | grep "job.batch/configure-corfu condition met"; then
  echo "Successfull deployment!"
else
  echo "Failed deployment!"
  exit 1
fi

#benchmarks
#echo pause 30 sec...
#sleep 30

helm install corfu-benchmarks corfu-benchmarks

sleep 15
echo get pods
kubectl get pods

export POD_NAME=$(kubectl get pods --namespace default -l "app.kubernetes.io/name=corfu-benchmarks,app.kubernetes.io/instance=corfu-benchmarks" -o jsonpath="{.items[0].metadata.name}")
kubectl logs ${POD_NAME}
