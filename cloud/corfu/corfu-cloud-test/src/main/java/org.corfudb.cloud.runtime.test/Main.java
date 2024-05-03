package org.corfudb.cloud.runtime.test;

import com.google.common.reflect.TypeToken;
import java.util.Map;
import org.corfudb.infrastructure.logreplication.proto.Sample;
import org.corfudb.infrastructure.logreplication.proto.LogReplicationMetadata.LogReplicationMetadataKey;
import org.corfudb.infrastructure.logreplication.proto.LogReplicationMetadata.LogReplicationMetadataVal;
import org.corfudb.runtime.CorfuOptions;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.CorfuRecord;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TableOptions;
import org.corfudb.runtime.collections.TxnContext;
import org.corfudb.runtime.CorfuStoreMetadata.TableName;
import org.corfudb.runtime.CorfuStoreMetadata.TableDescriptors;
import org.corfudb.runtime.CorfuStoreMetadata.TableMetadata;
import org.corfudb.runtime.view.ObjectsView;
import org.corfudb.util.NodeLocator;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * This tutorial demonstrates a simple Corfu application.
 *
 */
public class Main {
    private static final String USAGE = "Usage: HelloCorfu [-c <conf>]\n"
            + "Options:\n"
            + " -c <conf>     Set the configuration host and port  [default: localhost:9000]\n";

    /**
     * Internally, the corfuRuntime interacts with the CorfuDB service over TCP/IP sockets.
     *
     * @param host specifies the IP:port of the CorfuService
     *                            The configuration string has format "hostname:port", for example, "localhost:9090".
     * @return a CorfuRuntime object, with which Corfu applications perform all Corfu operations
     */
    private static CorfuRuntime getRuntimeAndConnect(String host, boolean tlsEnabled, String keyStore, String keyStorePassword, String trustStore, String trustStorePassword) {

        NodeLocator loc = NodeLocator.builder().host(host).port(9000).build();

        CorfuRuntime.CorfuRuntimeParameters.CorfuRuntimeParametersBuilder builder = CorfuRuntime.CorfuRuntimeParameters
                .builder()
                .connectionTimeout(Duration.ofSeconds(20))
                .layoutServers(Collections.singletonList(loc));
        if (tlsEnabled) {
            builder.tlsEnabled(tlsEnabled)
                    .keyStore(keyStore)
                    .ksPasswordFile(keyStorePassword)
                    .trustStore(trustStore)
                    .tsPasswordFile(trustStorePassword);
        }
        CorfuRuntime runtime = CorfuRuntime.fromParameters(builder.build());
        runtime.connect();
        return runtime;
    }

    // Sample code
    public static void main(String[] args) throws Exception {
        System.out.println("Start application. Got args: " + Arrays.toString(args));
        // Parse the options given, using docopt.
        /*
        Map<String, Object> opts =
                new Docopt(USAGE)
                        .withVersion(GitRepositoryState.getRepositoryState().describe)
                        .parse(args);
        String corfuConfigurationString = (String) opts.get("-c");
        */
        /**
         * First, the application needs to instantiate a CorfuRuntime,
         * which is a Java object that contains all of the Corfu utilities exposed to applications.
         */
        String ip  = "localhost";
        int job = 3;
        boolean tlsEnabled = false;
        String keyStore = "";
        String keyStorePassword = "";
        String trustStore = "";
        String trustStorePassword = "";

        if(args.length>=1) {
            ip  = args[0];
        }
        if (args.length >= 2) {
            job = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            keyStore = args[2];
        }
        if (args.length >= 4) {
            keyStorePassword = args[3];
        }
        if (args.length >= 5) {
            trustStore = args[4];
        }
        if (args.length >= 6) {
            trustStorePassword = args[5];
        }

        if (args.length == 6) {
            tlsEnabled = true;
        }

        if (job >= 1) {
            if (job != 2) {
                test(ip, tlsEnabled, keyStore, keyStorePassword, trustStore, trustStorePassword);
            }
            validate(ip, tlsEnabled, keyStore, keyStorePassword, trustStore, trustStorePassword);
        }
    }

    public static void test(String ip, boolean tlsEnabled, String keyStore, String keyStorePassword, String trustStore, String trustStorePassword) throws Exception {
        CorfuRuntime runtimeSource = getRuntimeAndConnect(ip, tlsEnabled, keyStore, keyStorePassword, trustStore, trustStorePassword);
        CorfuRuntime runtimeSink = getRuntimeAndConnect("corfu2-0.corfu2-headless.default.svc.cluster.local",
                tlsEnabled, keyStore, keyStorePassword, trustStore, trustStorePassword);

        CorfuStore corfuStoreSource = new CorfuStore(runtimeSource);
        CorfuStore corfuStoreSink = new CorfuStore(runtimeSink);

        String NAMESPACE = "LR-Test";
        String streamA = "MyTestTable";

        Table<Sample.StringKey, Sample.IntValue, Sample.Metadata> mapA = corfuStoreSource.openTable(
                NAMESPACE,
                streamA,
                Sample.StringKey.class,
                Sample.IntValue.class,
                Sample.Metadata.class,
                TableOptions.builder().schemaOptions(
                                CorfuOptions.SchemaOptions.newBuilder()
                                        .setIsFederated(true)
                                        .build())
                        .build()
        );

        Table<Sample.StringKey, Sample.IntValue, Sample.Metadata> mapASink = corfuStoreSink.openTable(
                NAMESPACE,
                streamA,
                Sample.StringKey.class,
                Sample.IntValue.class,
                Sample.Metadata.class,
                TableOptions.builder().schemaOptions(
                                CorfuOptions.SchemaOptions.newBuilder()
                                        .setIsFederated(true)
                                        .build())
                        .build()
        );

        int totalEntries = 200;
        int startIndex = 0;

        int maxIndex = totalEntries + startIndex;
        for (int i = startIndex; i < maxIndex; i++) {
            try (TxnContext txn = corfuStoreSource.txn(NAMESPACE)) {
                txn.putRecord(mapA, Sample.StringKey.newBuilder().setKey(String.valueOf(i)).build(),
                        Sample.IntValue.newBuilder().setValue(i).build(), null);
                txn.commit();
            }
        }

        try (TxnContext txn = corfuStoreSource.txn(NAMESPACE)) {
            int tableSize = txn.getTable(streamA).count();
            System.out.println("Size of source table after adding entries is: " + tableSize);
            txn.commit();
        }
    }

    public static void validate(String ip, boolean tlsEnabled, String keyStore, String keyStorePassword, String trustStore, String trustStorePassword) throws Exception {
        CorfuRuntime runtimeSource = getRuntimeAndConnect(ip, tlsEnabled, keyStore, keyStorePassword, trustStore, trustStorePassword);
        CorfuRuntime runtimeSink = getRuntimeAndConnect("corfu2-0.corfu2-headless.default.svc.cluster.local",
                tlsEnabled, keyStore, keyStorePassword, trustStore, trustStorePassword);

        CorfuStore corfuStoreSource = new CorfuStore(runtimeSource);
        CorfuStore corfuStoreSink = new CorfuStore(runtimeSink);

        String NAMESPACE = "LR-Test";
        String streamA = "MyTestTable";

        Table<Sample.StringKey, Sample.IntValue, Sample.Metadata> mapA = corfuStoreSource.openTable(
                NAMESPACE,
                streamA,
                Sample.StringKey.class,
                Sample.IntValue.class,
                Sample.Metadata.class,
                TableOptions.builder().schemaOptions(
                                CorfuOptions.SchemaOptions.newBuilder()
                                        .setIsFederated(true)
                                        .build())
                        .build()
        );

        Table<Sample.StringKey, Sample.IntValue, Sample.Metadata> mapASink = corfuStoreSink.openTable(
                NAMESPACE,
                streamA,
                Sample.StringKey.class,
                Sample.IntValue.class,
                Sample.Metadata.class,
                TableOptions.builder().schemaOptions(
                                CorfuOptions.SchemaOptions.newBuilder()
                                        .setIsFederated(true)
                                        .build())
                        .build()
        );

        while (true) {
            try (TxnContext txn = corfuStoreSource.txn(NAMESPACE)) {
                int tableSize = txn.getTable(streamA).count();
                System.out.println("Size of source table is: " + tableSize);
                txn.commit();
            }

            try (TxnContext txn = corfuStoreSink.txn(NAMESPACE)) {
                int tableSize = txn.getTable(streamA).count();

                System.out.println("Size of sink table is: " + tableSize);
                txn.commit();

                if (tableSize == 200) {
                    break;
                }
            }
            TimeUnit.SECONDS.sleep(5);
        }
    }
}