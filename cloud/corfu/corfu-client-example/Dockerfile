FROM openjdk:21-bullseye

ADD ./build/libs/corfu-runtime-client-example.jar /app/

WORKDIR /app

CMD java -cp *.jar org.corfudb.cloud.runtime.example.Main "$@"
