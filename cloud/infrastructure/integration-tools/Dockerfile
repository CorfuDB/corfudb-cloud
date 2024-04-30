FROM openjdk:21-bullseye

RUN apt update \
    && apt install -y ca-certificates curl \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo \
         "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian \
         $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
        tee /etc/apt/sources.list.d/docker.list > /dev/null

RUN apt update && apt install -y docker-ce-cli

ADD ./lib /app/lib/
ADD ./*.jar /app/
ADD ./bin/ /app/bin

WORKDIR /app

CMD bin/integration-tools.sh --help
