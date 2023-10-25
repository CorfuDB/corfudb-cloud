FROM openjdk:11-jdk-bullseye

RUN apt-get update && apt-get install -y iputils-ping

COPY build/libs/corfu-benchmarks-1.0.0-jmh.jar /benchmarks/
COPY config.yaml /benchmarks/
COPY logback-benchmarks.xml /benchmarks/

WORKDIR /benchmarks

CMD java -cp corfu-benchmarks-1.0.0-jmh.jar \
  -Dlogback.configurationFile=logback-benchmarks.xml \
  org.corfudb.benchmarks.cluster.cloud.CloudNativeClusterBenchmark