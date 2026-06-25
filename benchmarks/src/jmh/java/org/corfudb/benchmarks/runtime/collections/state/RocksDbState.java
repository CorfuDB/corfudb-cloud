package org.corfudb.benchmarks.runtime.collections.state;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.corfudb.benchmarks.runtime.collections.ExtensibleCacheBenchmark;
import org.corfudb.benchmarks.runtime.collections.helper.CorfuTableBenchmarkHelper;
import org.corfudb.benchmarks.runtime.collections.helper.ValueGenerator.StaticValueGenerator;
import org.corfudb.benchmarks.util.SizeUnit;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.DiskBackedCorfuTable;
import org.corfudb.runtime.collections.cache.ExtensibleCache;
import org.corfudb.runtime.object.PersistenceOptions;
import org.corfudb.runtime.object.RocksDbStore;
import org.corfudb.runtime.object.RocksDbStore.IndexMode;
import org.corfudb.runtime.object.RocksDbStore.StoreMode;
import org.corfudb.util.serializer.Serializers;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public abstract class RocksDbState {
    private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    static {
        RocksDB.loadLibrary();
    }

    @Getter
    CorfuTableBenchmarkHelper helper;

    ExtensibleCache<Integer, String> getCache() throws Exception {
        PersistenceOptions persistenceOptions = PersistenceOptions.builder()
                .dataPath(ExtensibleCacheBenchmark.dbPath)
                .storeMode(StoreMode.PERSISTENT)
                .indexMode(IndexMode.NON_INDEX)
                .build();

        WriteOptions writeOptions = new WriteOptions()
                .setDisableWAL(true)
                .setSync(false);

        Options defaultOptions = new Options()
                .setUseFsync(false)
                .setCreateIfMissing(true);
        RocksDbStore<DiskBackedCorfuTable<Integer, String>> rocksDbStore = new RocksDbStore<>(
                defaultOptions, writeOptions, persistenceOptions
        );

        return new ExtensibleCache<>(rocksDbStore, Serializers.getDefaultSerializer());
    }

    void init(int dataSize, int tableSize) throws Exception {
        log.info("Initialization...");

        ExtensibleCache<Integer, String> cache = getCache();

        StaticValueGenerator valueGenerator = new StaticValueGenerator(dataSize);
        helper = CorfuTableBenchmarkHelper.builder()
                .valueGenerator(valueGenerator)
                .cache(cache)
                .dataSize(dataSize)
                .tableSize(tableSize)
                .build()
                .check();
    }

    void stop() throws Exception {
        helper.getCache().close();
        //cleanDbDir();
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
        public void init() throws Exception {
            init(dataSize, tableSize);
            helper.fillTable();
        }

        @TearDown
        public void tearDown() throws Exception {
            stop();
        }
    }

    @Slf4j
    @State(Scope.Benchmark)
    public static class RocksDbStateForPut extends RocksDbState {

        @Param({"512", "1024", "4096"})
        @Getter
        public int dataSize;

        /**
         * Keys distribution
         */
        @Getter
        protected int tableSize = SizeUnit.HUNDRED_K.getValue();

        @Setup
        public void init() throws Exception {
            init(dataSize, tableSize);
        }

        @TearDown
        public void tearDown() throws Exception {
            stop();
        }
    }
}
