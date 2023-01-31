package org.corfudb.benchmarks.cluster.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.reflect.TypeToken;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.benchmarks.cluster.cloud.CloudNativeClusterBenchmarkStateUtil.CorfuStoreAndTable;
import org.corfudb.benchmarks.runtime.collections.helper.ValueGenerator;
import org.corfudb.benchmarks.runtime.collections.helper.ValueGenerator.DynamicValueGenerator;
import org.corfudb.benchmarks.util.DataGenerator;
import org.corfudb.protocols.wireprotocol.Token;
import org.corfudb.runtime.CorfuCompactorManagement.StringKey;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.MultiCheckpointWriter;
import org.corfudb.runtime.collections.CorfuDynamicKey;
import org.corfudb.runtime.collections.CorfuDynamicRecord;
import org.corfudb.runtime.collections.PersistentCorfuTable;
import org.corfudb.runtime.collections.TxnContext;
import org.corfudb.runtime.view.ObjectOpenOption;
import org.corfudb.runtime.view.TableRegistry;
import org.corfudb.util.serializer.DynamicProtobufSerializer;
import org.corfudb.utils.CommonTypes.Uuid;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * The benchmark measures corfu's table performance
 * see: docs/benchmarks/corfu-table.md
 */
@Slf4j
public class CloudNativeClusterBenchmark {
    // Default name of the CorfuTable created by CorfuClient
    public static final String DEFAULT_STREAM_NAME = "stream";

    public static final String CORFU_NAMESPACE = "namespace";

    public static final BenchmarkConfig CONFIG = loadConfig();

    /**
     * Cloud Native Cluster benchmark
     * <p>
     * <a href="http://hg.openjdk.java.net/code-tools/jmh/file/bcec9a03787f/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_15_Asymmetric.java">Mixed/asymmetric benchmark</a>
     *
     * @param args args
     * @throws RunnerException jmh exception
     */
    public static void main(String[] args) throws Exception {
        log.info("Run corfu cloud native benchmark");

        String benchmarkName = CloudNativeClusterBenchmark.class.getSimpleName();

        String benchmarkFileName = benchmarkName + "-" + System.currentTimeMillis() + ".csv";
        Path benchmarksReportFile = Paths.get("/", "var", "log", "corfu", benchmarkFileName);
        benchmarksReportFile.toFile().getParentFile().mkdirs();

        log.info("Start " + benchmarkName);

        Options opt = new OptionsBuilder()
                .include(benchmarkName)
                .shouldFailOnError(true)

                .resultFormat(ResultFormatType.CSV)
                .result(benchmarksReportFile.toString())

                .param("dataSize", CONFIG.benchmark.put.dataSize)
                .param("dataSizeForGetOperation", CONFIG.benchmark.get.dataSizeForGetOperation)

                .param("putNumRuntimes", CONFIG.benchmark.put.putNumRuntimes)
                .param("putNumTables", CONFIG.benchmark.put.putNumTables)

                .param("getNumRuntimes", CONFIG.benchmark.get.getNumRuntimes)
                .param("getNumTables", CONFIG.benchmark.get.getNumTables)

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

    public static BenchmarkConfig loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        File configFile = Paths.get("config.yaml").toAbsolutePath().toFile();

        try {
            return mapper.readValue(configFile, BenchmarkConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    @State(Scope.Benchmark)
    @Getter
    @Slf4j
    public static class ClusterBenchmarkStateForGet {
        @Param({"1024"})
        public int dataSizeForGetOperation;
        @Param({"1"})
        public int getNumRuntimes;
        @Param({"1"})
        public int getNumTables;

        private final int tableSize = 10_000;

        private final CloudNativeClusterBenchmarkStateUtil util = new CloudNativeClusterBenchmarkStateUtil();

        /**
         * Init benchmark state
         */
        @Setup
        public void init() throws Exception {
            log.info("Init benchmark state");
            util.initRuntimesAndTables(getNumRuntimes, getNumTables);
            fillTable();
        }

        /**
         * Fill corfu table with random values
         */
        public void fillTable() {
            ValueGenerator valueGenerator = new DynamicValueGenerator(dataSizeForGetOperation);

            for (int i = 0; i < getTableSize(); i++) {
                for (CorfuStoreAndTable storeAndTable : util.tables) {
                    Uuid key = Uuid.newBuilder()
                            .setMsb(i)
                            .setLsb(i)
                            .build();
                    StringKey value = StringKey.newBuilder()
                            .setKey(valueGenerator.value())
                            .build();
                    try (TxnContext tx = storeAndTable.store.txn(CloudNativeClusterBenchmarkStateUtil.CorfuStoreAndTable.NAMESPACE)) {
                        tx.putRecord(storeAndTable.table, key, value, null);
                        tx.commit();
                    }
                }
            }
        }

        /**
         * Tear down the state after the benchmark is finished
         */
        @TearDown
        public void tearDown() {
            for (CorfuStoreAndTable storeAndTable : util.tables) {
                storeAndTable.table.clearAll();
            }

            //checkpointing();

            for (CorfuRuntime corfuClient : util.corfuClients) {
                corfuClient.shutdown();
            }
        }

    }

    @State(Scope.Benchmark)
    @Getter
    @Slf4j
    public static class ClusterBenchmarkStateForPut {
        @Param({"1024"})
        public int dataSize;
        @Param({"1"})
        public int putNumRuntimes;
        @Param({"1"})
        public int putNumTables;

        private String data;

        private final CloudNativeClusterBenchmarkStateUtil util = new CloudNativeClusterBenchmarkStateUtil();

        /**
         * Init benchmark state
         */
        @Setup
        public void init() throws Exception {
            log.info("Init benchmark state");
            data = DataGenerator.generateDataString(dataSize);
            util.initRuntimesAndTables(putNumRuntimes, putNumTables);
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
    }

    /**
     * Measure corfu table `put` operation performance
     *
     * @param state benchmark state
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Group("mixedBenchmark")
    @GroupThreads(1)
    public void putOperation(ClusterBenchmarkStateForPut state) {
        StringKey value = StringKey.newBuilder()
                .setKey(state.data)
                .build();

        int keyId = state.util.rnd.nextInt(state.util.rnd.nextInt(Integer.MAX_VALUE));
        Uuid key = Uuid.newBuilder()
                .setMsb(keyId)
                .setLsb(keyId)
                .build();

        CorfuStoreAndTable storeAndTable = state.util.getRandomTable(state.putNumTables);
        try (TxnContext tx = storeAndTable.store.txn(CloudNativeClusterBenchmarkStateUtil.CorfuStoreAndTable.NAMESPACE)) {
            tx.putRecord(storeAndTable.table, key, value, null);
            tx.commit();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Group("mixedBenchmark")
    @GroupThreads(9)
    public void getOperation(ClusterBenchmarkStateForGet state) {

        int keyId = state.util.rnd.nextInt(state.tableSize - 1);
        Uuid key = Uuid.newBuilder()
                .setMsb(keyId)
                .setLsb(keyId)
                .build();
        CorfuStoreAndTable storeAndTable = state.util.getRandomTable(state.getNumTables);
        try (TxnContext tx = storeAndTable.store.txn(CloudNativeClusterBenchmarkStateUtil.CorfuStoreAndTable.NAMESPACE)) {
            tx.getRecord(storeAndTable.table, key);
            tx.commit();
        }
    }

}
