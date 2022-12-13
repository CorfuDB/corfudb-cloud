#!/usr/bin/env bash

set -e

kubectl apply -f rbac.yaml
kubectl apply -f experiment.yaml
kubectl apply -f engine.yaml
while [[ "not found" == *"$(kubectl get chaosresult corfu-pod-delete-pod-delete)"*  ]]
do
  echo "Waiting for chaos result..."
  sleep 5
done

kubectl get chaosresult corfu-pod-delete-pod-delete -o yaml
kubectl delete sa pod-delete-sa
kubectl delete chaosexperiment pod-delete
kubectl delete chaosengine corfu-pod-delete
kubectl delete chaosresult corfu-pod-delete-pod-delete
# Check logs:
# kubectl logs pod-delete-wu20af--1-cg2wk -f
