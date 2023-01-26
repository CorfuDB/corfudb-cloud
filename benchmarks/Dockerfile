FROM openjdk:8-jdk-bullseye

RUN apt-get update && apt-get install -y iputils-ping

COPY build/libs/corfu-benchmarks-1.0.0-jmh.jar /benchmarks/
COPY config.yaml /benchmarks/

WORKDIR /benchmarks

CMD java -cp corfu-benchmarks-1.0.0-jmh.jar org.corfudb.benchmarks.cluster.CloudNativeClusterBenchmark