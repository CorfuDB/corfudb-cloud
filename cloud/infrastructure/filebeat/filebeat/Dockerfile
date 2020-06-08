FROM docker.elastic.co/beats/filebeat:7.7.1

ADD filebeat.yml /usr/share/filebeat/filebeat.yml

CMD filebeat -e --strict.perms=false --once run
