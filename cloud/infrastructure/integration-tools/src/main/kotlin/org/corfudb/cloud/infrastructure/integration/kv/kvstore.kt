package org.corfudb.cloud.infrastructure.integration.kv

import com.fasterxml.jackson.databind.ObjectMapper
import org.rocksdb.*

object RocksDbManager {
    private val db: RocksDB
    private val config = RocksDbConfig()
    val provider: RocksDbProvider

    init {
        RocksDB.loadLibrary()
        db = RocksDB.open(config.opts, config.dbDir)
        provider = RocksDbProvider(config, db, mutableListOf())
    }
}

class RocksDbProvider(
        private val config: RocksDbConfig,
        val db: RocksDB,
        private val cfHandles: MutableList<ColumnFamilyHandle>
) {

    fun findColumnFamily(name: String): ColumnFamilyHandle? {
        return cfHandles.find { handle -> String(handle.name) == name }
    }

    fun getColumnFamily(name: String): ColumnFamilyHandle {
        var cf = findColumnFamily(name)
        if (cf == null) {
            cf = db.createColumnFamily(ColumnFamilyDescriptor(name.toByteArray()))
            cfHandles.add(cf)
            return cf
        }

        return cf
    }
}

class RocksDbConfig(
        val dbDir: String = "/data/processing.db",
        val opts: Options = Options().setCreateIfMissing(true)
) {
    val dbOpts = DBOptions(opts)

    fun listColumnFamilies(): List<ColumnFamilyDescriptor> {
        return RocksDB
                .listColumnFamilies(opts, dbDir)
                .map { cf -> ColumnFamilyDescriptor(cf) }
                .toMutableList()
    }
}

class KvStore(private val provider: RocksDbProvider, private val mapper: ObjectMapper) {
    private val opts: WriteOptions = WriteOptions().setDisableWAL(true)
    private val db = provider.db

    fun <V> get(key: ProcessingKey, valueType: Class<V>): V {
        val cf = provider.findColumnFamily(key.aggregationUnit)
        val value: ByteArray = db.get(cf, mapper.writeValueAsBytes(key))
        return mapper.readValue(value, valueType)
    }

    fun <V> put(key: ProcessingKey, value: V) {
        val cf = provider.getColumnFamily(key.aggregationUnit)
        db.put(cf, opts, mapper.writeValueAsBytes(key), mapper.writeValueAsBytes(value))
    }

    fun findAll(aggregationUnit: String): List<ProcessingMessage> {
        val cf = provider.getColumnFamily(aggregationUnit)
        db.newIterator(cf).use { iter ->
            iter.seekToFirst()
            val values = mutableListOf<ProcessingMessage>()
            while (iter.isValid) {
                val message = mapper.readValue(iter.value(), ProcessingMessage::class.java)
                values.add(message)
                iter.next()
            }
            return values.toList()
        }
    }
}

data class ProcessingKey(
        val aggregationUnit: String,
        val timestamp: Long = System.currentTimeMillis()
)

data class ProcessingMessage(val key: ProcessingKey, val message: String) {
    companion object {
        fun new(aggregationUnit: String, message: String) = ProcessingMessage(ProcessingKey(aggregationUnit), message)
    }
}