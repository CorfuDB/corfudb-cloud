
# The Cloud Native Corfu Cluster benchmarks

We had the opportunity to run a Java benchmark for Corfu using JMH (Java Microbenchmark Harness) on the cloud (AWS). 
JMH is a widely used tool for writing and running benchmarks in Java, 
and it is known for its ability to provide accurate and reliable performance measurements.

The main goal of the benchmarks was to measure the performance of the put and get operations in Corfu, 
and we chose to run it on AWS for several reasons. 
Firstly, running the benchmark on the cloud allows for large-scale benchmarking and testing. 
This is important because it allows us to have a better understanding of the performance characteristics of the application under different loads and conditions.


### The Setup
AWS, m5.4xlarge
 - CPU: 16x
 - 64 GB 
 - Network: Up to 10 Gbps

## CorfuTable put and get operations

Benchmark:
  - Measuring the get and put operations performance
  - CorfuDb is 3 node cluster that is run on three m5.4xlarge nodes in Kubernetes
  - The benchmark node is run on one m5.4xlarge node in Kubernetes
  - Measuring corfu performance for different concurrency operations and data size.
    For the put operation we measure the combination (combinatorics) of:
     - data_size: 4kb, 65kb, 512kb, 1mb
     - number of corfu runtimes: 1, 4
     - number of corfu tables: 1, 4
  
###JMH benchmark report:
```
Benchmark                                          (dataSize)  (numRuntimes)  (numTables)   Mode  Cnt      Score   Error  Units
CloudNativeClusterBenchmark.getOperation                 4096              1            1  thrpt       19230.241          ops/s
CloudNativeClusterBenchmark.getOperation                 4096              1            4  thrpt       18120.424          ops/s
CloudNativeClusterBenchmark.getOperation                 4096              4            1  thrpt       19722.650          ops/s
CloudNativeClusterBenchmark.getOperation                 4096              4            4  thrpt       17391.365          ops/s
CloudNativeClusterBenchmark.getOperation                65536              1            1  thrpt       18901.826          ops/s
CloudNativeClusterBenchmark.getOperation                65536              1            4  thrpt       19138.425          ops/s
CloudNativeClusterBenchmark.getOperation                65536              4            1  thrpt       20755.804          ops/s
CloudNativeClusterBenchmark.getOperation                65536              4            4  thrpt       17107.167          ops/s
CloudNativeClusterBenchmark.putOperation                 4096              1            1  thrpt         159.906          ops/s
CloudNativeClusterBenchmark.putOperation                 4096              1            4  thrpt         150.198          ops/s
CloudNativeClusterBenchmark.putOperation                 4096              4            1  thrpt         157.510          ops/s
CloudNativeClusterBenchmark.putOperation                 4096              4            4  thrpt         155.481          ops/s
CloudNativeClusterBenchmark.putOperation                65536              1            1  thrpt         114.499          ops/s
CloudNativeClusterBenchmark.putOperation                65536              1            4  thrpt         123.166          ops/s
CloudNativeClusterBenchmark.putOperation                65536              4            1  thrpt         112.632          ops/s
CloudNativeClusterBenchmark.putOperation                65536              4            4  thrpt         130.262          ops/s
CloudNativeClusterBenchmark.putOperation               524288              1            1  thrpt          40.382          ops/s
CloudNativeClusterBenchmark.putOperation               524288              1            4  thrpt          57.727          ops/s
CloudNativeClusterBenchmark.putOperation               524288              4            1  thrpt          40.984          ops/s
CloudNativeClusterBenchmark.putOperation               524288              4            4  thrpt          58.752          ops/s
CloudNativeClusterBenchmark.putOperation              1048576              1            1  thrpt          30.663          ops/s
CloudNativeClusterBenchmark.putOperation              1048576              1            4  thrpt          32.350          ops/s
CloudNativeClusterBenchmark.putOperation              1048576              4            1  thrpt          32.446          ops/s
CloudNativeClusterBenchmark.putOperation              1048576              4            4  thrpt           9.832          ops/s
```
