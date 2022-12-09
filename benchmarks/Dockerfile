FROM openjdk:8-jdk-bullseye

COPY build/libs/corfu-benchmarks-1.0.0-jmh.jar /benchmarks/

WORKDIR /benchmarks

CMD java -cp corfu-benchmarks-1.0.0-jmh.jar org.corfudb.benchmarks.cluster.CloudNativeClusterBenchmark