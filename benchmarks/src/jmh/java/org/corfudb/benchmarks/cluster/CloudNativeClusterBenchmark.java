package org.corfudb.benchmarks.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.reflect.TypeToken;
import com.google.protobuf.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.benchmarks.runtime.collections.helper.ValueGenerator;
import org.corfudb.benchmarks.runtime.collections.helper.ValueGenerator.DynamicValueGenerator;
import org.corfudb.benchmarks.util.DataGenerator;
import org.corfudb.protocols.wireprotocol.Token;
import org.corfudb.runtime.CorfuCompactorManagement.StringKey;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters.CorfuRuntimeParametersBuilder;
import org.corfudb.runtime.MultiCheckpointWriter;
import org.corfudb.runtime.collections.CorfuDynamicKey;
import org.corfudb.runtime.collections.CorfuDynamicRecord;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.PersistentCorfuTable;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TableOptions;
import org.corfudb.runtime.collections.TxnContext;
import org.corfudb.runtime.view.ObjectOpenOption;
import org.corfudb.runtime.view.TableRegistry;
import org.corfudb.util.NodeLocator;
import org.corfudb.util.serializer.DynamicProtobufSerializer;
import org.corfudb.utils.CommonTypes.Uuid;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * The benchmark measures corfu's table performance
 * see: docs/benchmarks/corfu-table.md
 */
@Slf4j
public class CloudNativeClusterBenchmark {
    // Default name of the CorfuTable created by CorfuClient
    private static final String DEFAULT_STREAM_NAME = "stream";

    private static final String CORFU_NAMESPACE = "namespace";

    private static final BenchmarkConfig CONFIG = loadConfig();

    /**
     * Cloud Native Cluster benchmark
     *
     * @param args args
     * @throws RunnerException jmh exception
     */
    public static void main(String[] args) throws Exception {
        log.info("Run corfu cloud native benchmark");

        String benchmarkName = CloudNativeClusterBenchmark.class.getSimpleName();

        Path benchmarksReportFile = Paths.get("benchmarks", "report", benchmarkName + ".csv");
        benchmarksReportFile.toFile().getParentFile().mkdirs();

        log.info("Start {}", benchmarkName);

        Options opt = new OptionsBuilder()
                .include(benchmarkName)
                .shouldFailOnError(true)

                //.resultFormat(ResultFormatType.CSV)
                //.result(benchmarksReportFile.toString())
                .param("dataSize", CONFIG.benchmark.put.dataSize)
                .param("dataSizeForGetOperation", CONFIG.benchmark.get.dataSizeForGetOperation)

                .param("putNumRuntimes", CONFIG.benchmark.put.putNumRuntimes)
                .param("putNumTables", CONFIG.benchmark.put.putNumTables)

                .param("getNumRuntimes", CONFIG.benchmark.get.getNumRuntimes)
                .param("getNumTables", CONFIG.benchmark.get.getNumTables)
                .build();

        new Runner(opt).run();

        log.info("Finishing benchmark!");
        TimeUnit.MINUTES.sleep(CONFIG.benchmark.coolOffPeriodMinutes);
    }

    private static BenchmarkConfig loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        File configFile = Paths.get("config.yaml").toAbsolutePath().toFile();

        try {
            return mapper.readValue(configFile, BenchmarkConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    public static class CloudNativeClusterBenchmarkStateUtil {
        protected final Random rnd = new Random();

        public final List<CorfuRuntime> corfuClients = new ArrayList<>();
        public final List<CorfuStoreAndTable> tables = new ArrayList<>();

        protected CorfuStoreAndTable createDefaultCorfuTable(CorfuRuntime runtime, String tableName) throws Exception {
            CorfuStore store = new CorfuStore(runtime);

            Table<Uuid, StringKey, Message> table = store.openTable(
                    CORFU_NAMESPACE,
                    tableName,
                    Uuid.class,
                    StringKey.class,
                    null,
                    TableOptions.fromProtoSchema(StringKey.class)
            );

            return CorfuStoreAndTable.builder()
                    .store(store)
                    .table(table)
                    .build();
        }

        protected CorfuRuntime buildCorfuClient() {
            String namespace = Optional
                    .ofNullable(System.getenv("POD_NAMESPACE"))
                    .orElseThrow(() -> new IllegalStateException("Namespace is not defined"));

            NodeLocator loc = NodeLocator.builder()
                    .host(String.format("corfu-0.corfu-headless.%s.svc.cluster.local", namespace))
                    .port(9000)
                    .build();

            CorfuRuntimeParametersBuilder builder = CorfuRuntime.CorfuRuntimeParameters
                    .builder()
                    .connectionTimeout(Duration.ofSeconds(5))
                    .layoutServers(Collections.singletonList(loc))
                    .tlsEnabled(true)
                    .keyStore(CONFIG.tls.keystore)
                    .ksPasswordFile(CONFIG.tls.keystorePassword)
                    .trustStore(CONFIG.tls.truststore)
                    .tsPasswordFile(CONFIG.tls.truststorePassword);

            CorfuRuntime runtime = CorfuRuntime.fromParameters(builder.build());
            runtime.connect();
            return runtime;
        }

        protected void initRuntimesAndTables(int numRuntimes, int numTables) throws Exception {
            for (int i = 0; i < numRuntimes; i++) {
                corfuClients.add(buildCorfuClient());
            }

            for (int i = 0; i < numTables; i++) {
                CorfuRuntime runtime = getRandomRuntime(numRuntimes);
                CorfuStoreAndTable table = createDefaultCorfuTable(runtime, DEFAULT_STREAM_NAME + i);
                tables.add(table);
            }
        }

        public CorfuRuntime getRandomRuntime(int numRuntimes) {
            return corfuClients.get(rnd.nextInt(numRuntimes));
        }

        /**
         * Returns a random table from the list of corfu tables generated during initialization
         *
         * @return random corfu table from the list
         */
        public CorfuStoreAndTable getRandomTable(int numTables) {
            return tables.get(rnd.nextInt(numTables));
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
                    try (TxnContext tx = storeAndTable.store.txn(CorfuStoreAndTable.NAMESPACE)) {
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

        private void checkpointing() {
            log.info("Shutdown. Execute checkpointing");
            DynamicProtobufSerializer dynamicProtobufSerializer = new DynamicProtobufSerializer(util.getRandomRuntime(getNumRuntimes));

            CorfuRuntime runtime = util.getRandomRuntime(getNumRuntimes);
            MultiCheckpointWriter<PersistentCorfuTable<CorfuDynamicKey, CorfuDynamicRecord>> mcw = new MultiCheckpointWriter<>();

            runtime.getSerializers().registerSerializer(dynamicProtobufSerializer);

            String registryStreamName = TableRegistry.getFullyQualifiedTableName(
                    TableRegistry.CORFU_SYSTEM_NAMESPACE,
                    TableRegistry.REGISTRY_TABLE_NAME
            );
            PersistentCorfuTable<CorfuDynamicKey, CorfuDynamicRecord> tableRegistry = runtime.getObjectsView().build()
                    .setTypeToken(new TypeToken<PersistentCorfuTable<CorfuDynamicKey, CorfuDynamicRecord>>() {
                    })
                    .setStreamName(registryStreamName)
                    .setSerializer(dynamicProtobufSerializer)
                    .addOpenOption(ObjectOpenOption.CACHE)
                    .open();

            String descriptorTableName = TableRegistry.getFullyQualifiedTableName(
                    TableRegistry.CORFU_SYSTEM_NAMESPACE,
                    TableRegistry.PROTOBUF_DESCRIPTOR_TABLE_NAME
            );
            PersistentCorfuTable<CorfuDynamicKey, CorfuDynamicRecord> descriptorTable = runtime.getObjectsView().build()
                    .setTypeToken(new TypeToken<PersistentCorfuTable<CorfuDynamicKey, CorfuDynamicRecord>>() {
                    })
                    .setStreamName(descriptorTableName)
                    .setSerializer(dynamicProtobufSerializer)
                    .addOpenOption(ObjectOpenOption.CACHE)
                    .open();

            for (CorfuStoreAndTable storeAndTable : util.tables) {
                PersistentCorfuTable<CorfuDynamicKey, CorfuDynamicRecord> corfuTable = runtime.getObjectsView().build()
                        .setTypeToken(new TypeToken<PersistentCorfuTable<CorfuDynamicKey, CorfuDynamicRecord>>() {
                        })
                        .setStreamName(storeAndTable.table.getFullyQualifiedTableName())
                        .setSerializer(dynamicProtobufSerializer)
                        .addOpenOption(ObjectOpenOption.CACHE)
                        .open();

                mcw.addMap(corfuTable);
            }
            mcw.addMap(tableRegistry);
            mcw.addMap(descriptorTable);
            Token trimPoint = mcw.appendCheckpoints(runtime, "checkpointer");

            runtime.getAddressSpaceView().prefixTrim(trimPoint);
            runtime.getAddressSpaceView().gc();
            runtime.getSerializers().clearCustomSerializers();
            runtime.shutdown();
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
    @Warmup(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
    @BenchmarkMode(Mode.Throughput)
    @Threads(4)
    @Fork(1)
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
        try (TxnContext tx = storeAndTable.store.txn(CorfuStoreAndTable.NAMESPACE)) {
            tx.putRecord(storeAndTable.table, key, value, null);
            tx.commit();
        }
    }

    @Benchmark
    @Warmup(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
    @BenchmarkMode(Mode.Throughput)
    @Threads(4)
    @Fork(1)
    public void getOperation(ClusterBenchmarkStateForGet state, Blackhole blackhole) {

        int keyId = state.util.rnd.nextInt(state.tableSize - 1);
        Uuid key = Uuid.newBuilder()
                .setMsb(keyId)
                .setLsb(keyId)
                .build();
        CorfuStoreAndTable storeAndTable = state.util.getRandomTable(state.getNumTables);
        try (TxnContext tx = storeAndTable.store.txn(CorfuStoreAndTable.NAMESPACE)) {
            tx.getRecord(storeAndTable.table, key);
            tx.commit();
        }
    }

    @Builder
    public static final class CorfuStoreAndTable {
        public static final String NAMESPACE = "namespace";

        @NonNull
        public Table<Uuid, StringKey, Message> table;
        @NonNull
        public CorfuStore store;
    }

    @Getter
    @ToString
    @NoArgsConstructor
    public static class BenchmarkConfig {
        private BenchmarkTlsConfig tls;
        private BenchmarkParams benchmark;

        @Getter
        @ToString
        @NoArgsConstructor
        private static class BenchmarkParams {
            public PutParams put;
            public GetParams get;
            public Integer coolOffPeriodMinutes;
        }

        @Getter
        @ToString
        @NoArgsConstructor
        private static class BenchmarkTlsConfig {
            private String keystore;
            private String keystorePassword;

            private String truststore;
            private String truststorePassword;
        }

        @Getter
        @ToString
        @NoArgsConstructor
        private static class PutParams {
            private String[] dataSize;
            private String[] putNumRuntimes;
            private String[] putNumTables;
        }

        @Getter
        @ToString
        @NoArgsConstructor
        private static class GetParams {
            private String[] dataSizeForGetOperation;
            private String[] getNumRuntimes;
            private String[] getNumTables;
        }
    }
}
