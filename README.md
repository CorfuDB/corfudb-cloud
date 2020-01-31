# corfudb-tests
A testing framework + Test cases for CorfuDB 

### The project contains following modules
 - tests
   Contains a set of integration tests that covers  various database testing scenarios.
 
 - benchmarks. 
   The Documentation is [here](benchmarks/docs)

   The benchmark module is built on top of Java MicroBenchmark Harness and the Universe framework.
   JMH Samples https://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/

   It provides tools to create and run a corfu cluster on different environments (VM, DOCKER, PROCESSES)
   and measure performance characteristics of the cluster by running different benchmarks.