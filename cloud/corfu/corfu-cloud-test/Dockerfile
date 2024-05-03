FROM openjdk:8-jdk-alpine3.8

ADD ./build/libs/corfu-cloud-test.jar /app/

WORKDIR /app

CMD java -cp *.jar org.corfudb.cloud.runtime.test.Main "$@"
