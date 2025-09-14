FROM openjdk:21-bullseye

ADD ./lib /app/lib/
ADD ./*.jar /app/
ADD ./*.json /app/
ADD ./corfu-dashboard /app/corfu-dashboard

ADD ./bin/ /app/bin

WORKDIR /app

CMD bin/kibana-tools.sh --help
