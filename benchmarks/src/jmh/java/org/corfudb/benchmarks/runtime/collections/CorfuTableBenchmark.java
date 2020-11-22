package org.corfudb.benchmarks.runtime.collections;

import org.corfudb.benchmarks.runtime.collections.helper.CorfuTableBenchmarkHelper;
import org.corfudb.benchmarks.runtime.collections.state.EhCacheState.EhCacheStateForGet;
import org.corfudb.benchmarks.runtime.collections.state.EhCacheState.EhCacheStateForPut;
import org.corfudb.benchmarks.runtime.collections.state.HashMapState.HashMapStateForGet;
import org.corfudb.benchmarks.runtime.collections.state.HashMapState.HashMapStateForPut;
import org.corfudb.benchmarks.runtime.collections.state.RocksDbState;
import org.corfudb.benchmarks.runtime.collections.state.RocksDbState.RocksDbStateForPut;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * CorfuTable benchmark.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 1, time = 3)
@Measurement(iterations = 3, time = 10)
@Threads(1)
@Fork(value = 1, jvmArgsAppend = {"-Xms4g", "-Xmx4g"})
public class CorfuTableBenchmark {

    /**
     * Corfu table benchmark
     *
     * @param args args
     * @throws RunnerException jmh runner exception
     */
    public static void main(String[] args) throws RunnerException {

        String benchmarkName = CorfuTableBenchmark.class.getSimpleName();

        Options opt = new OptionsBuilder()
                .include(benchmarkName)
                .shouldFailOnError(true)

                .resultFormat(ResultFormatType.CSV)
                .result("build/" + benchmarkName + ".csv")

                .build();

        new Runner(opt).run();
    }

    /**
     * Put operation benchmark for RocksDb
     *
     * @param state     benchmark state
     * @param blackhole jmh blackhole
     */
    @Benchmark
    public void rocksDbPut(RocksDbStateForPut state, Blackhole blackhole) {
        CorfuTableBenchmarkHelper helper = state.getHelper();
        Integer key = helper.generate();
        String result = helper.getUnderlyingMap().put(key, helper.generateValue());
        blackhole.consume(result);
    }

    /**
     * Put operation benchmark for EhCache
     *
     * @param state     benchmark state
     * @param blackhole jmh blackhole
     */
    @Benchmark
    public void ehCachePut(EhCacheStateForPut state, Blackhole blackhole) {
        CorfuTableBenchmarkHelper helper = state.getHelper();
        String result = helper.getTable().put(helper.generate(), helper.generateValue());
        blackhole.consume(result);
    }

    /**
     * Get operation benchmark for RocksDb
     *
     * @param state     benchmark state
     * @param blackhole jmh blackhole
     */
    @Benchmark
    public void rocksDbGet(RocksDbState.RocksDbStateForGet state, Blackhole blackhole) {
        CorfuTableBenchmarkHelper helper = state.getHelper();
        int key = helper.generate();
        String value = helper.getTable().get(key);

        if (value == null) {
            throw new IllegalStateException("The value not found in the cache. Key: " + key);
        }

        blackhole.consume(value);
    }

    /**
     * Get operation benchmark for ehCache
     *
     * @param state     benchmark state
     * @param blackhole jmh blackhole
     */
    @Benchmark
    public void ehCacheGet(EhCacheStateForGet state, Blackhole blackhole) {
        CorfuTableBenchmarkHelper helper = state.getHelper();
        int key = helper.generate();
        String value = helper.getTable().get(key);
        if (value == null) {
            throw new IllegalStateException("The value not found in the cache. Key: " + key);
        }
        blackhole.consume(value);
    }

    /**
     * Put operation benchmark for HashMap
     *
     * @param state     benchmark state
     * @param blackhole jmh blackhole
     */
    @Benchmark
    public void hashMapPut(HashMapStateForPut state, Blackhole blackhole) {
        CorfuTableBenchmarkHelper helper = state.getHelper();
        String value = helper.getTable().put(helper.generate(), helper.generateValue());
        blackhole.consume(value);
    }

    /**
     * Get operation benchmark for HashMap
     *
     * @param state     benchmark state
     * @param blackhole jmh blackhole
     */
    @Benchmark
    public void hashMapGet(HashMapStateForGet state, Blackhole blackhole) {
        CorfuTableBenchmarkHelper helper = state.getHelper();
        String value = helper.getTable().get(helper.generate());
        blackhole.consume(value);
    }

    /**
     * Get/Put 50x50 load for HashMap
     *
     * @param state     benchmark state
     * @param blackhole jmh blackhole
     */
    @Benchmark
    public void readWriteRatio50x50(HashMapStateForGet state, Blackhole blackhole) {
        CorfuTableBenchmarkHelper helper = state.getHelper();
        int rndValue = helper.generate();
        String rndValueStr = String.valueOf(rndValue);
        String value = helper.getTable().put(rndValue, rndValueStr);
        blackhole.consume(value);

        value = helper.getTable().get(rndValue);
        blackhole.consume(value);
    }
}
