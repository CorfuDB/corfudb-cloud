package org.corfudb.cloud.infrastructure.integration.kv

import com.fasterxml.jackson.databind.ObjectMapper
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.DBOptions
import org.rocksdb.Options
import org.rocksdb.RocksDB
import org.rocksdb.WriteOptions
import java.time.Instant
import java.time.format.DateTimeFormatter

object RocksDbManager {
    private val db: RocksDB
    private val config = RocksDbConfig()
    val provider: RocksDbProvider

    init {
        val cfHandles: MutableList<ColumnFamilyHandle> = mutableListOf()
        val columnFamilies = config.listColumnFamilies()
        db = if (columnFamilies.isEmpty()) {
            RocksDB.open(config.opts, config.dbDir)
        } else {
            RocksDB.open(config.dbOpts, config.dbDir, columnFamilies, cfHandles)
        }
        provider = RocksDbProvider(config, db, cfHandles)
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
        var columnFamilies = RocksDB
            .listColumnFamilies(opts, dbDir)
            .map { cf -> ColumnFamilyDescriptor(cf) }

        if (columnFamilies.isEmpty()) {
            columnFamilies = listOf(ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY))
        }

        return columnFamilies
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

    fun put(message: ProcessingMessage) {
        val cf = provider.getColumnFamily(message.key.aggregationUnit)
        db.put(cf, opts, mapper.writeValueAsBytes(message.key), mapper.writeValueAsBytes(message))
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
    val timestamp: Long = System.currentTimeMillis(),
    val date: String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timestamp))
)

data class ProcessingMessage(val key: ProcessingKey, val message: String) {
    companion object {
        fun new(aggregationUnit: String, message: String) = ProcessingMessage(ProcessingKey(aggregationUnit), message)
    }
}