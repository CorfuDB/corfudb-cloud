package org.corfudb.benchmarks.runtime.collections.state;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.corfudb.benchmarks.runtime.collections.experiment.rocksdb.RocksDbMap;
import org.corfudb.benchmarks.runtime.collections.helper.CorfuTableBenchmarkHelper;
import org.corfudb.benchmarks.runtime.collections.helper.ValueGenerator.StaticValueGenerator;
import org.corfudb.benchmarks.util.SizeUnit;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.DiskBackedCorfuTable;
import org.corfudb.runtime.collections.ICorfuTable;
import org.corfudb.runtime.collections.PersistedCorfuTable;
import org.corfudb.runtime.object.PersistenceOptions;
import org.corfudb.util.serializer.Serializers;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

@Slf4j
public abstract class RocksDbState {
    private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    @Getter
    CorfuRuntime corfuRuntime;

    @Getter
    CorfuTableBenchmarkHelper helper;

    private final String tableName = "DiskBackedTable";

    private final Path dbPath = Paths.get(
            FilenameUtils.getName(TMP_DIR), "corfu", "rt", "persistence", "rocks_db"
    );

    RocksDbMap<Integer, String> getRocksDbMap() {
        return RocksDbMap.<Integer, String>builder()
                .dbPath(dbPath)
                .keyType(Integer.class)
                .valueType(String.class)
                .build();
    }

    private void cleanDbDir() throws IOException {
        File dbDir = dbPath.toFile();
        FileUtils.deleteDirectory(dbDir);
        FileUtils.forceMkdir(dbDir);
    }

    void init(int dataSize, int tableSize) throws IOException, RocksDBException {
        log.info("Initialization...");

        cleanDbDir();

        PersistenceOptions.PersistenceOptionsBuilder persistenceOptions = PersistenceOptions.builder()
                .dataPath(dbPath);
        StaticValueGenerator valueGenerator = new StaticValueGenerator(dataSize);
        ICorfuTable<Integer, String> table = corfuRuntime.getObjectsView().build()
                .setTypeToken(PersistedCorfuTable.<Integer, String>getTypeToken())
                .setArguments(persistenceOptions.build(), DiskBackedCorfuTable.defaultOptions, Serializers.PRIMITIVE)
                .setStreamName(tableName)
                .setSerializer(Serializers.PRIMITIVE)
                .open();

        helper = CorfuTableBenchmarkHelper.builder()
                .valueGenerator(valueGenerator)
                .table(table)
                .dataSize(dataSize)
                .tableSize(tableSize)
                .build()
                .check();
    }

    void stop() throws RocksDBException, IOException {
        helper.getTable().close();
        cleanDbDir();
    }

    @State(Scope.Benchmark)
    @Getter
    @Slf4j
    public static class RocksDbStateForGet extends RocksDbState {

        @Param({"64", "256"})
        @Getter
        public int dataSize;

        @Getter
        @Param({"100000", "1000000"})
        protected int tableSize;

        @Setup
        public void init() throws IOException, RocksDBException {
            init(dataSize, tableSize);
            helper.fillTable();
        }

        @TearDown
        public void tearDown() throws RocksDBException, IOException {
            stop();
        }
    }

    @Slf4j
    @State(Scope.Benchmark)
    public static class RocksDbStateForPut extends RocksDbState {

        @Param({"64", "256"})
        @Getter
        public int dataSize;

        /**
         * Keys distribution
         */
        @Getter
        protected int tableSize = SizeUnit.HUNDRED_K.getValue();

        @Setup
        public void init() throws IOException, RocksDBException {
            init(dataSize, tableSize);
        }

        @TearDown
        public void tearDown() throws IOException, RocksDBException {
            stop();
        }
    }
}
