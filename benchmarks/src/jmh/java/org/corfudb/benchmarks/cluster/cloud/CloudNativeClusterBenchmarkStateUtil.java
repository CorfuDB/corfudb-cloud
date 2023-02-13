package org.corfudb.benchmarks.cluster.cloud;

import com.google.common.reflect.TypeToken;
import com.google.protobuf.Message;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
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
import org.corfudb.runtime.view.ObjectOpenOption;
import org.corfudb.runtime.view.TableRegistry;
import org.corfudb.util.NodeLocator;
import org.corfudb.util.serializer.DynamicProtobufSerializer;
import org.corfudb.utils.CommonTypes.Uuid;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
public class CloudNativeClusterBenchmarkStateUtil {
    protected final Random rnd = new Random();

    public final List<CorfuRuntime> corfuClients = new ArrayList<>();
    public final List<CorfuStoreAndTable> tables = new ArrayList<>();

    protected CorfuStoreAndTable createDefaultCorfuTable(CorfuRuntime runtime, String tableName) throws Exception {
        CorfuStore store = new CorfuStore(runtime);

        Table<Uuid, StringKey, Message> table = store.openTable(
                CloudNativeClusterBenchmark.CORFU_NAMESPACE,
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
                .keyStore(CloudNativeClusterBenchmark.CONFIG.tls.keystore)
                .ksPasswordFile(CloudNativeClusterBenchmark.CONFIG.tls.keystorePassword)
                .trustStore(CloudNativeClusterBenchmark.CONFIG.tls.truststore)
                .tsPasswordFile(CloudNativeClusterBenchmark.CONFIG.tls.truststorePassword);

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
            CorfuStoreAndTable table = createDefaultCorfuTable(runtime, CloudNativeClusterBenchmark.DEFAULT_STREAM_NAME + i);
            tables.add(table);
        }
    }

    public CorfuRuntime getRandomRuntime(int numRuntimes) {
        return corfuClients.get(rnd.nextInt(numRuntimes));
    }

    public void checkpointing(int numRuntimes) {
        log.info("Shutdown. Execute checkpointing");
        DynamicProtobufSerializer dynamicProtobufSerializer = new DynamicProtobufSerializer(getRandomRuntime(numRuntimes));

        CorfuRuntime runtime = getRandomRuntime(numRuntimes);
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

    /**
     * Returns a random table from the list of corfu tables generated during initialization
     *
     * @return random corfu table from the list
     */
    public CorfuStoreAndTable getRandomTable(int numTables) {
        return tables.get(rnd.nextInt(numTables));
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
