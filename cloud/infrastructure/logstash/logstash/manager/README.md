Logstash-tools

how to use: 
`docker run -ti --rm -e ELASTIC_SEARCH_HOST="10.10.10.10:9200" -e ELASTIC_INDEX_NAME=my_index -e SERVER="192.168.1.1" -v $(pwd)/corfu.gc.log:/var/log/corfu/jvm/corfu.gc.log corfudb/logstash-tools`
