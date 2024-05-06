FROM docker.elastic.co/beats/filebeat:7.10.0

USER root

RUN yum install -y yum-utils \
    && yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo \
    && yum install -y docker-ce-cli

ADD filebeat.yml /usr/share/filebeat/filebeat.yml

CMD filebeat -e --strict.perms=false --once run
