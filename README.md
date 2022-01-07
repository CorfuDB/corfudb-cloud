# corfudb-cloud:
 - [Log Aggregation Platform](cloud/infrastructure/docs/lap)
 - [An orchestration framework (Corfu Universe Framework)](universe)
 - Integration tests for CorfuDB
 - Benchmarks

### The project contains following modules
 - ##### Log Aggregation Platform
   
   Log aggregation, search, analysis, and data visualization in real-time. Built on top of ELK stack and Docker.
   
 - ##### Corfu Universe Framework
   
   Managing Corfu cluster on various development environments like Docker, VSphere, Local Environment.  
   
 - ##### Tests
   
   - A set of integration tests that use Corfu Universe Framework and cover various database testing scenarios.
   - A set of compatibility tests that deploy a 3-node Corfu server cluster with different versions and test their backward compatibility.
   [Documentation](tests/docs/compatibility-tests) 
 
 - ##### Benchmarks 
   
   [Documentation](benchmarks/docs/benchmarks)

   The benchmark module is built on top of Java MicroBenchmark Harness and the Universe framework.
   JMH Samples https://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/

   It provides tools to create and run a corfu cluster on different environments (VM, DOCKER, PROCESSES)
   and measure performance characteristics of the cluster by running different benchmarks.
