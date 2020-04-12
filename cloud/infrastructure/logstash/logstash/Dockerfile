FROM docker.elastic.co/logstash/logstash:7.6.0

ADD configuration/patterns /usr/share/logstash/patterns
ADD configuration/pipeline /usr/share/logstash/pipeline
ADD configuration/templates /usr/share/logstash/templates

ADD manager/pipeline/logstash.conf /usr/share/logstash/pipeline/logstash.conf
ADD manager/config/logstash.yml /usr/share/logstash/config/logstash.yml

#Define ELASTIC_SEARCH_HOST, ELASTIC_INDEX_NAME, SERVER_IP

CMD logstash
