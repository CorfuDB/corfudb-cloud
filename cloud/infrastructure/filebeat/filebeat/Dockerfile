FROM docker.elastic.co/beats/filebeat:7.10.0

ADD filebeat.yml /usr/share/filebeat/filebeat.yml

CMD filebeat -e --strict.perms=false --once run
