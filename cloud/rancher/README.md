# Rancher K8s Cluster Cloud Config

This is a step-by-step guide on how to set up a K8s cluster via Rancher
with a VSphere cloud provider.

## Cloud Credentials

1. In Rancher UI open Cloud Credentials.
2. Give a name.
3. `Cloud Credential Type` select `VMware vSphere`.
4. `vCenter Server` type in the IP of your vCenter server.
5. For `Port` type in `443`.
6. For `Username` select your vSphere admin username.
7. For `Password` select your vSphere admin password.

## Node Template

0. For a `Cloud Provider` select a `vSphere` cloud provider.
1. `Cloud Credentials` -> Name of the cloud credentials you've defined earlier.
2. `Data Center` select your vSphere data center.
3. `Data Store` select your vSphere data store.
4. `CPUs` : 2+
5. `Disk`: 20K+
6. `Memory`: 16K+
7. `Creation method`: Install from boot2docker.
8. `Networks`: VM Network.
9. Make sure `disk.enableUUID` is set to TRUE.
10. Create a template.

## Cluster
0. For a `Cloud Provider` select a `vSphere` cloud provider.
1. Add two node pools: master and worker.
- Master: Count is 1, Template: your Node Template, Auto Replace: 5 min, etcd: on, Control Plane: on.
- Worker: Count is 3, Template: your Node Template, Auto Replace: 5 min, Worker: on.
3. For `Cluster Options` for `Kubernetes Options` for a `Cloud Provider` choose `Custom`. 
4. To the right of `Cluster Options` click `Edit as YAML`.
5. In the current project copy a file `k8s/rancher/cluster-options-template.yaml`.
6. For a copied file replace the fields that have the comments next to them accordingly and save a file as `cluster-options.yaml`.
7. Click `Read from a file` and upload `cluster-options.yaml`.
8. Click `Create`.

 