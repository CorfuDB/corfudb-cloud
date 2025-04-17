package org.corfudb.benchmarks.runtime.collections;

import org.apache.commons.lang3.RandomStringUtils;
import org.corfudb.benchmarks.runtime.collections.helper.CorfuTableBenchmarkHelper;
import org.corfudb.benchmarks.runtime.collections.state.RocksDbState.RocksDbStateForPut;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.rocksdb.RocksDB;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class ExtensibleCacheBenchmark {
    static {
        RocksDB.loadLibrary();
    }

    public static final Path dbPath = Paths.get(
            "/tmp",
            "corfu",
            "extensible_cache",
            RandomStringUtils.randomAlphabetic(10)
    );

    public static void main(String[] args) throws RunnerException {
        String benchmarkName = ExtensibleCacheBenchmark.class.getSimpleName();

        Options opt = new OptionsBuilder()
                .include(benchmarkName)
                .shouldFailOnError(true)
                .build();

        new Runner(opt).run();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 1, time = 3)
    @Measurement(iterations = 1, time = 3, timeUnit = TimeUnit.SECONDS)
    @Threads(8)
    @Fork(value = 1, jvmArgsAppend = {"-Xms4g", "-Xmx4g"})
    public void put(RocksDbStateForPut state, Blackhole blackhole) {
        CorfuTableBenchmarkHelper helper = state.getHelper();
        Integer key = helper.generate();
        helper.getCache().put(key, helper.generateValue());
        String value = helper.getCache().get(key);
        blackhole.consume(value);
    }

}
