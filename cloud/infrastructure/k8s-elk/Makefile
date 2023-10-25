
clean_cluster:
	k3d cluster delete corfu-lap

create_cluster:
	k3d cluster create corfu-lap
	echo cluster has been created
	sleep 120

deploy: create_cluster deploy_eck deploy_elastic deploy_kibana


# https://www.elastic.co/guide/en/cloud-on-k8s/current/k8s-deploy-eck.html
deploy_eck:
	kubectl create -f https://download.elastic.co/downloads/eck/2.9.0/crds.yaml
	kubectl apply -f https://download.elastic.co/downloads/eck/2.9.0/operator.yaml
	sleep 120

deploy_elastic:
	kubectl apply -f elastic.yaml
	sleep 120

deploy_logstash:
	kubectl apply -k logstash

uninstall_logstash:
	kustomize build logstash | kubectl delete -f -

deploy_kibana:
	kubectl apply -f kibana.yaml
	sleep 120

kibana_port_forward:
	kubectl port-forward service/quickstart-kb-http 5601

elastic_port_forward:
	kubectl port-forward service/quickstart-es-http 9200

elastic_print_password:
	kubectl get secret quickstart-es-elastic-user -o=jsonpath='{.data.elastic}' | base64 --decode; echo

elastic_log_monitoring:
	kubectl -n elastic-system logs -f statefulset.apps/elastic-operator

elastic_describe:
	kubectl describe crd elasticsearch

deploy_lap_server:
	 kubectl apply -k log-aggregation-server/base

uninstall_lap_server:
	kustomize build log-aggregation-server/base | kubectl delete -f -
