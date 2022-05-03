#### Corfu installation 

 - install k3d: https://k3d.io/v5.4.1/
 - create k8s cluster: `k3d cluster create corfu --volume /tmp/k3dvol:/tmp/k3dvol --agents 2`
 
 - install cert-manager: https://cert-manager.io/docs/installation/helm/#steps

 - clone corfu-cloud repository: `git clone git@github.com:CorfuDB/corfudb-cloud.git`
 - open `infrastructure` directory: `cd corfudb-cloud/cloud/infrastructure/corfu`
 
 - install helm chart: `helm install corfu corfu --set persistence.enabled=true` 


##### Check corfu status
 - check all pods: `kubectl get pods`
 - check certificates: `kubectl describe secrets corfu-certificate`
 - check bootstrap container: `kubectl describe pod configure-corfu--1-26cw4`
 - check layout generation: `kubectl logs configure-corfu--1-26cw4 -c create-layout`
 - check cert manager state: `kubectl get pods --namespace cert-manager`
 - check bootstrap logs: `kubectl logs configure-corfu--1-26cw4 -c bootstrap-corfu`
