package org.corfudb.benchmarks.cluster;

import com.google.common.reflect.TypeToken;
import com.google.protobuf.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
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
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.runtime.collections.PersistentCorfuTable;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TableOptions;
import org.corfudb.runtime.collections.TxnContext;
import org.corfudb.runtime.view.ObjectOpenOption;
import org.corfudb.runtime.view.TableRegistry;
import org.corfudb.util.NodeLocator;
import org.corfudb.util.serializer.DynamicProtobufSerializer;
import org.corfudb.util.serializer.ISerializer;
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
                .build();

        new Runner(opt).run();

        log.info("Finishing benchmark!");
        for (int i = 0; i < TimeUnit.HOURS.toMinutes(1); i++) {
            TimeUnit.MINUTES.sleep(1);
            Thread.yield();
        }
    }


    public static abstract class AbstractCloudNativeClusterBenchmarkState {
        protected final Random rnd = new Random();

        public final List<CorfuRuntime> corfuClients = new ArrayList<>();
        public final List<CorfuStoreAndTable> tables = new ArrayList<>();

        protected CorfuStoreAndTable createDefaultCorfuTable(CorfuRuntime runtime, String tableName) throws Exception {
            final String namespace = "namespace";

            CorfuStore store = new CorfuStore(runtime);

            Table<Uuid, StringKey, Message> table = store.openTable(
                    namespace,
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
                    .keyStore("/certs/keystore.jks")
                    .ksPasswordFile("/password/password")
                    .trustStore("/certs/truststore.jks")
                    .tsPasswordFile("/password/password");

            CorfuRuntime runtime = CorfuRuntime.fromParameters(builder.build());
            runtime.connect();
            return runtime;
        }

        protected void initRuntimesAndTables() throws Exception {
            for (int i = 0; i < getNumRuntimes(); i++) {
                corfuClients.add(buildCorfuClient());
            }

            for (int i = 0; i < getNumTables(); i++) {
                CorfuRuntime runtime = getRandomRuntime();
                CorfuStoreAndTable table = createDefaultCorfuTable(runtime, DEFAULT_STREAM_NAME + i);
                tables.add(table);
            }
        }

        public CorfuRuntime getRandomRuntime() {
            return corfuClients.get(rnd.nextInt(getNumRuntimes()));
        }

        /**
         * Returns a random table from the list of corfu tables generated during initialization
         *
         * @return random corfu table from the list
         */
        public CorfuStoreAndTable getRandomTable() {
            return tables.get(rnd.nextInt(getNumTables()));
        }

        public abstract int getNumRuntimes();

        public abstract int getNumTables();
    }

    @State(Scope.Benchmark)
    @Getter
    @Slf4j
    public static class ClusterBenchmarkStateForGet extends AbstractCloudNativeClusterBenchmarkState {

        @Param({"4096", "65536", "131072"})
        public int dataSize;

        @Param({"1", "4"})
        public int numRuntimes;

        @Param({"1", "4"})
        public int numTables;

        private final int tableSize = 10_000;

        /**
         * Init benchmark state
         */
        @Setup
        public void init() throws Exception {
            log.info("Init benchmark state");
            initRuntimesAndTables();
            fillTable();
        }

        /**
         * Fill corfu table with random values
         */
        public void fillTable() {
            ValueGenerator valueGenerator = new DynamicValueGenerator(dataSize);

            for (int i = 0; i < getTableSize(); i++) {
                for (CorfuStoreAndTable storeAndTable : tables) {
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
            for (CorfuStoreAndTable storeAndTable : tables) {
                storeAndTable.table.clearAll();
            }

            //checkpointing();

            for (CorfuRuntime corfuClient : corfuClients) {
                corfuClient.shutdown();
            }
        }

        private void checkpointing() {
            log.info("Shutdown. Execute checkpointing");
            DynamicProtobufSerializer dynamicProtobufSerializer = new DynamicProtobufSerializer(getRandomRuntime());

            CorfuRuntime runtime = getRandomRuntime();
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

            for (CorfuStoreAndTable storeAndTable : tables) {
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
    public static class ClusterBenchmarkStateForPut extends AbstractCloudNativeClusterBenchmarkState {

        @Param({"4096", "65536", "524288", "1048576"})
        public int dataSize;

        @Param({"1", "4"})
        public int numRuntimes;

        @Param({"1", "4"})
        public int numTables;

        private String data;

        /**
         * Init benchmark state
         */
        @Setup
        public void init() throws Exception {
            log.info("Init benchmark state");
            data = DataGenerator.generateDataString(dataSize);
            initRuntimesAndTables();
        }

        /**
         * Tear down the state after the benchmark is finished
         */
        @TearDown
        public void tearDown() {
            for (CorfuStoreAndTable storeAndTable : tables) {
                storeAndTable.table.clearAll();
            }

            for (CorfuRuntime corfuClient : corfuClients) {
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
    @Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.MINUTES)
    @BenchmarkMode(Mode.Throughput)
    @Threads(4)
    @Fork(1)
    public void clusterBenchmarkPutOperation(ClusterBenchmarkStateForPut state) {
        StringKey value = StringKey.newBuilder()
                .setKey(state.data)
                .build();

        int keyId = state.rnd.nextInt(state.rnd.nextInt(Integer.MAX_VALUE));
        Uuid key = Uuid.newBuilder()
                .setMsb(keyId)
                .setLsb(keyId)
                .build();

        CorfuStoreAndTable storeAndTable = state.getRandomTable();
        try (TxnContext tx = storeAndTable.store.txn(CorfuStoreAndTable.NAMESPACE)) {
            tx.putRecord(storeAndTable.table, key, value, null);
            tx.commit();
        }
    }

    @Benchmark
    @Warmup(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.MINUTES)
    @BenchmarkMode(Mode.Throughput)
    @Threads(4)
    @Fork(1)
    public void clusterBenchmarkGetOperation(ClusterBenchmarkStateForGet state, Blackhole blackhole) {

        int keyId = state.rnd.nextInt(state.tableSize - 1);
        Uuid key = Uuid.newBuilder()
                .setMsb(keyId)
                .setLsb(keyId)
                .build();
        CorfuStoreAndTable storeAndTable = state.getRandomTable();
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
}
