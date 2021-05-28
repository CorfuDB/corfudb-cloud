FROM corfudb/corfu-server:0.3.1-SNAPSHOT

RUN apk update && apk add openssh openssl openrc

RUN adduser --disabled-password test && \
    sh -c 'echo "test:test"' | chpasswd > /dev/null 2>&1 && \
    sh -c 'echo "root ALL=NOPASSWD: ALL"' >> /etc/sudoers

RUN mkdir -p /run/openrc && \
    touch /run/openrc/softlevel && \
    rc-update add sshd default

RUN rc-status

ADD docker-entrypoint.sh /usr/local/bin
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
#make sure we get fresh keys
RUN rm -rf /etc/ssh/ssh_host_rsa_key /etc/ssh/ssh_host_dsa_key

EXPOSE 22
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]