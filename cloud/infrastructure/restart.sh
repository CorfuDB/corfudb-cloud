helm delete corfu
kubectl delete pvc corfu-data-corfu-0
kubectl delete pvc corfu-data-corfu-1
kubectl delete pvc corfu-data-corfu-2

rm -rf /tmp/k3dvol
mkdir -p /tmp/k3dvol

k3d cluster delete corfu
k3d cluster create corfu --volume /tmp/k3dvol:/tmp/k3dvol --agents 2

helm repo add jetstack https://charts.jetstack.io
helm repo update


echo install cert manager
helm install \
  cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --version v1.8.0 \
  --set installCRDs=true

echo download shiva
sleep 30

kubectl run mytest --image=corfu/corfu-server:cloud
kubectl delete pod mytest

echo wait 90 sec and install shiva
sleep 90

helm install corfu corfu
