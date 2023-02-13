package org.corfudb.benchmarks.cluster.cloud;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.benchmarks.cluster.cloud.BenchmarkConfig.RandomizedPutParams;
import org.corfudb.benchmarks.cluster.cloud.CloudNativeClusterBenchmarkStateUtil.CorfuStoreAndTable;
import org.corfudb.benchmarks.util.DataGenerator;
import org.corfudb.runtime.CorfuCompactorManagement.StringKey;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.TxnContext;
import org.corfudb.utils.CommonTypes.Uuid;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * The benchmark measures corfu's table performance
 * see: docs/benchmarks/corfu-table.md
 */
@Slf4j
public class RandomizedCloudNativeClusterBenchmark {
    private static final BenchmarkConfig CONFIG = CloudNativeClusterBenchmark.loadConfig();

    /**
     * Cloud Native Cluster benchmark that uses random value of data size between the borders set by config
     *
     * @param args args
     * @throws RunnerException jmh exception
     */
    public static void main(String[] args) throws Exception {
        log.info("Run randomized corfu cloud native benchmark");

        String benchmarkName = RandomizedCloudNativeClusterBenchmark.class.getSimpleName();

        String benchmarkFileName = benchmarkName +  "-" + System.currentTimeMillis() + ".csv";
        Path benchmarksReportFile = Paths.get("/", "var", "log", "corfu", benchmarkFileName);
        benchmarksReportFile.toFile().getParentFile().mkdirs();

        log.info("Start " + benchmarkName);

        RandomizedPutParams putConfig = CONFIG.benchmark.randomizedPut;
        Options opt = new OptionsBuilder()
                .include(benchmarkName)
                .shouldFailOnError(true)

                .resultFormat(ResultFormatType.CSV)
                .result(benchmarksReportFile.toString())

                .param("minDataSize", putConfig.minDataSize)
                .param("maxDataSize", putConfig.maxDataSize)

                .param("numRuntimes", putConfig.numRuntimes)
                .param("numTables", putConfig.numTables)

                .warmupIterations(CONFIG.benchmark.warmup.iterations)
                .warmupTime(TimeValue.minutes(CONFIG.benchmark.warmup.timeInMinutes))

                .measurementIterations(CONFIG.benchmark.measurement.iterations)
                .measurementTime(TimeValue.minutes(CONFIG.benchmark.measurement.timeInMinutes))

                .threads(CONFIG.benchmark.threads)
                .forks(CONFIG.benchmark.forks)

                .build();

        new Runner(opt).run();

        log.info("Finishing benchmark!");
        TimeUnit.MINUTES.sleep(CONFIG.benchmark.coolOffPeriodMinutes);
    }

    @State(Scope.Benchmark)
    @Getter
    @Slf4j
    public static class RandomizedClusterBenchmarkStateForPut {
        @Param({"1024"})
        public int minDataSize;
        @Param({"2048"})
        public int maxDataSize;

        @Param({"1"})
        public int numRuntimes;
        @Param({"1"})
        public int numTables;

        private final CloudNativeClusterBenchmarkStateUtil util = new CloudNativeClusterBenchmarkStateUtil();

        /**
         * Init benchmark state
         */
        @Setup
        public void init() throws Exception {
            log.info("Init benchmark state");
            util.initRuntimesAndTables(numRuntimes, numTables);
        }

        /**
         * Tear down the state after the benchmark is finished
         */
        @TearDown
        public void tearDown() {
            for (CorfuStoreAndTable storeAndTable : util.tables) {
                storeAndTable.table.clearAll();
            }

            for (CorfuRuntime corfuClient : util.corfuClients) {
                corfuClient.shutdown();
            }
        }

        /**
         * Returns random value between min data size and max data size
         * @return random data size
         */
        public int getRandomDataSize() {
            int limit = maxDataSize - minDataSize;
            int randomValue = util.rnd.nextInt(limit);
            return util.rnd.nextInt(minDataSize + randomValue);
        }
    }

    /**
     * Measure corfu table `put` operation performance
     *
     * @param state benchmark state
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void putOperation(RandomizedClusterBenchmarkStateForPut state) {
        String data = DataGenerator.generateDataString(state.getRandomDataSize());

        StringKey value = StringKey.newBuilder()
                .setKey(data)
                .build();

        int keyId = state.util.rnd.nextInt(state.util.rnd.nextInt(Integer.MAX_VALUE));
        Uuid key = Uuid.newBuilder()
                .setMsb(keyId)
                .setLsb(keyId)
                .build();

        CorfuStoreAndTable storeAndTable = state.util.getRandomTable(state.numTables);
        try (TxnContext tx = storeAndTable.store.txn(CloudNativeClusterBenchmarkStateUtil.CorfuStoreAndTable.NAMESPACE)) {
            tx.putRecord(storeAndTable.table, key, value, null);
            tx.commit();
        }
    }
}
