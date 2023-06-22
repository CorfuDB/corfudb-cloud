
install_k8s_tools:
	curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
	curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
	chmod 700 get_helm.sh
	./get_helm.sh

run_cluster:
	k3d cluster delete corfu
	k3d cluster create corfu
	helm repo add jetstack https://charts.jetstack.io
	helm repo update

	helm install cert-manager jetstack/cert-manager \
		--namespace cert-manager \
		--create-namespace \
		--version v1.11.0 \
		--set installCRDs=true \
		--set prometheus.enabled=false

deploy:
	helm install corfu corfu --set persistence.enabled=true --set global.replicas=3
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
	helm repo update

	helm install prometheus prometheus-community/prometheus

	#corfu-0.corfu-headless.default.svc.cluster.local:9090
    #kubectl edit configmap prometheus-server
