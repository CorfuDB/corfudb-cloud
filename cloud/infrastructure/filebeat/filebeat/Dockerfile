FROM docker.elastic.co/beats/filebeat:7.6.2

ADD filebeat.yml /usr/share/filebeat/filebeat.yml

CMD filebeat -e --strict.perms=false --once run
