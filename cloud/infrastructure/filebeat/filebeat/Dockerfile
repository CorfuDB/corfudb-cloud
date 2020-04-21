FROM docker.elastic.co/beats/filebeat:7.6.0

ADD filebeat.yml /usr/share/filebeat/filebeat.yml
ADD inputs.d /usr/share/filebeat/inputs.d

CMD filebeat -e --strict.perms=false --once run
