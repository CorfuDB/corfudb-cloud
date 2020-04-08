FROM openjdk:8-jdk-alpine3.8

ADD ./lib /app/lib/
ADD ./app/*.jar /app/

WORKDIR /app

CMD java -cp "kibana-tools.jar:/app/lib/*" org.corfudb.cloud.infrastructure.kibana.tools.MainKt --help
