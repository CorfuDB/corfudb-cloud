#!/bin/zsh

cluster_setup() {
  k3d cluster delete corfu
  rm -rf /tmp/k3dvol

  k3d cluster create corfu \
    --volume /tmp/k3dvol:/tmp/k3dvol \
    -p "8082:30080@agent:0" \
    --agents 4
}

image_imports() {
  k3d image import corfudb/corfu-server:0.3.2-SNAPSHOT -c corfu
  k3d image import corfudb/corfu-server:0.4.0-SNAPSHOT -c corfu
  k3d image import corfudb/corfu-client-example:latest -c corfu
}

helm_setup() {
  helm repo add jetstack https://charts.jetstack.io
  helm repo update
  helm install cert-manager jetstack/cert-manager --namespace cert-manager --create-namespace --version v1.8.0 --set installCRDs=true
}

init_v1_cluster() {
  helm install corfu corfu --set tls.enabled=false --set tls.certificate.enabled=false --set global.replicas=3 --set image.repository=corfudb/corfu-server --set image.tag=0.3.2-SNAPSHOT
  helm install corfu2 corfu --set tls.enabled=false --set tls.certificate.enabled=false --set global.replicas=3 --set image.repository=corfudb/corfu-server --set image.tag=0.3.2-SNAPSHOT --set lr.name="log-replication2" --set nameOverride="corfu2" --set serviceAccount.name="corfu2" --set nameOverride="corfu2" --set fullnameOverride="corfu2" --set cluster.type="sink"
  sleep 30
}

init_v2_cluster() {
  helm install corfu corfu --set tls.enabled=false --set tls.certificate.enabled=false --set global.replicas=3 --set image.repository=corfudb/corfu-server --set image.tag=0.4.0-SNAPSHOT --set version.new=true
  helm install corfu2 corfu --set tls.enabled=false --set tls.certificate.enabled=false --set global.replicas=3 --set image.repository=corfudb/corfu-server --set image.tag=0.4.0-SNAPSHOT --set lr.name="log-replication2" --set nameOverride="corfu2" --set serviceAccount.name="corfu2" --set nameOverride="corfu2" --set fullnameOverride="corfu2" --set cluster.type="sink" --set version.new=true
  sleep 30
}

cluster_verify() {
  local lr_version=$1

  # Wait for Corfu to be ready
  while ! kubectl logs corfu-0 -c corfu | grep -q "DATA"; do
    echo "Corfu is not ready yet..."
    sleep 15
  done
  echo "Corfu is Ready!!!!"

  # Get the leader of the log replication
  lr_leader=""
  while true; do
    if kubectl logs log-replication-0 | grep -q "acquired"; then
      lr_leader="log-replication-0"
      break
    fi
    if kubectl logs log-replication-1 | grep -q "acquired"; then
      lr_leader="log-replication-1"
      break
    fi
    if kubectl logs log-replication-2 | grep -q "acquired"; then
      lr_leader="log-replication-2"
      break
    fi
  done

  echo "LR Leader is: $lr_leader"

  lr_ready_str=""
  if [ $lr_version = "V2" ]; then
    lr_ready_str="Received leadership response from node"
  else
    lr_ready_str="Negotiation complete"
  fi

  # Wait for the log replication leader to be ready
  while ! kubectl logs $lr_leader | grep -q $lr_ready_str; do
    echo "LR is not ready yet..."
    sleep 10
  done

  echo "Ready to Replicate!!!!"
}

cluster_upgrade() {
  helm upgrade corfu corfu --set tls.enabled=false --set tls.certificate.enabled=false --set global.replicas=3 --set image.repository=corfudb/corfu-server --set image.tag=0.4.0-SNAPSHOT --set version.new=true
  helm upgrade corfu2 corfu --set tls.enabled=false --set tls.certificate.enabled=false --set global.replicas=3 --set image.repository=corfudb/corfu-server --set image.tag=0.4.0-SNAPSHOT --set lr.name="log-replication2" --set nameOverride="corfu2" --set serviceAccount.name="corfu2" --set nameOverride="corfu2" --set fullnameOverride="corfu2" --set cluster.type="sink" --set version.new=true

  while kubectl describe pods --all-namespaces | grep -q "0.3.2-SNAPSHOT"; do
    echo "Waiting for pods to be re-imaged..."
    sleep 10
  done

  echo "Cluster upgrade complete!!!"
}


cluster_test() {
  echo "Writing Data To Source..."
  helm install corfu-client corfu-client-example-helm --set tls.enabled=false --set jobs.job=1

  while ! kubectl get pods -o wide | grep corfu-client | grep -q Completed; do
    echo "Waiting for test to finish..."
    sleep 5
  done


  helm uninstall corfu-client
  while kubectl get pods -o wide | grep -q corfu-client; do
    echo "Removing test agent..."
    sleep 5
  done

  echo "Test Complete!!!"
}

cluster_test_validate() {
    echo "Starting test validation!!!"
    helm install corfu-client corfu-client-example-helm --set tls.enabled=false --set jobs.job=2

    while ! kubectl get pods -o wide | grep corfu-client | grep -q Completed; do
      echo "Waiting for validation to complete..."
      sleep 5
    done

    helm uninstall corfu-client
    while kubectl get pods -o wide | grep -q corfu-client; do
      echo "Removing test agent..."
      sleep 5
    done

    echo "Validation Complete!!!"
}

cluster_setup
image_imports
helm_setup

init_v1_cluster
cluster_verify V1
cluster_test

cluster_upgrade

cluster_verify V2
cluster_test_validate