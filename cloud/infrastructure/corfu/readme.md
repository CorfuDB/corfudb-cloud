#### Corfu installation 

 - install k3d: https://k3d.io/v5.4.1/
 - create k8s cluster: `k3d cluster create corfu`
 
 - install cert-manager: https://cert-manager.io/docs/installation/

 - clone corfu-cloud repository: `git clone git@github.com:CorfuDB/corfudb-cloud.git`
 - open `infrastructure` directory: `cd corfudb-cloud/cloud/infrastructure/corfu`
 
 - install helm chart: `helm install corfu --generate-name` 
