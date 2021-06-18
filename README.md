# corfudb-cloud:
 - [Log Aggregation Platform](cloud/infrastructure/docs/lap)
 - An orchestration framework (Corfu Universe Framework)
 - Integration tests for CorfuDB
 - Benchmarks

### The project contains following modules
 - ##### Log Aggregation Platform
   
   Log aggregation, search, analysis, and data visualization in real-time. Built on top of ELK stack and Docker.
   
 - ##### Corfu Universe Framework
   
   Managing Corfu cluster on various development environments like Docker, VSphere, Local Environment.  
   
 - ##### Tests
   
   Contains a set of integration tests that use Corfu Universe Framework and cover various database testing scenarios.
 
 - ##### Benchmarks 
   
   [Documentation](benchmarks/docs)

   The benchmark module is built on top of Java MicroBenchmark Harness and the Universe framework.
   JMH Samples https://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/

   It provides tools to create and run a corfu cluster on different environments (VM, DOCKER, PROCESSES)
   and measure performance characteristics of the cluster by running different benchmarks.
