
1. Make sure that corfu is installed first.
2. Install cli chart: `helm install corfu-cli corfu-cli`
3. Exec into a running pod (make sure to get the name right): `kubectl exec -it corfu-cli-b6765f4bb-5p6vz -- bash`
4. `cd /usr/share/corfu/lib` 
5. List tables in default namespace `java -cp corfudb-tools-0.3.2-SNAPSHOT-shaded.jar org.corfudb.browser.CorfuStoreBrowserEditorMain --tlsEnabled=true --keystore=/certs/keystore.jks  --truststore=/certs/truststore.jks --ks_password=/password/password --truststore_password=/password/password --host=corfu-0.corfu-headless.default.svc.cluster.local --port=9000 --namespace=default --operation=listTables`
6. Add some data: 
