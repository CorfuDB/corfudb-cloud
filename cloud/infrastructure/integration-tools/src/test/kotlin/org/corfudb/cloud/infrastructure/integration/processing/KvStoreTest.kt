package org.corfudb.cloud.infrastructure.integration.processing


import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.corfudb.cloud.infrastructure.integration.kv.KvStore
import org.corfudb.cloud.infrastructure.integration.kv.ProcessingKey
import org.corfudb.cloud.infrastructure.integration.kv.ProcessingMessage
import org.corfudb.cloud.infrastructure.integration.kv.RocksDbConfig
import org.corfudb.cloud.infrastructure.integration.kv.RocksDbProvider
import org.junit.Test
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.RocksDB
import java.io.File
import kotlin.test.assertEquals

class KvStoreTest {
    init {
        RocksDB.loadLibrary()
    }

    @Test
    fun listColumnFamiliesTest() {
        val dbPath = "build/test.db"
        File(dbPath).deleteRecursively()
        File(dbPath).mkdirs()

        val cfHandles: MutableList<ColumnFamilyHandle> = mutableListOf()

        val config = RocksDbConfig(dbDir = dbPath)
        val columnFamilies = config.listColumnFamilies()

        val db = RocksDB.open(config.dbOpts, config.dbDir, columnFamilies, cfHandles)
        db.close()
    }

    @Test
    fun columnFamiliesTest() {
        val dbPath = "build/test.db"
        File(dbPath).deleteRecursively()
        File(dbPath).mkdirs()

        val config = RocksDbConfig(dbDir = dbPath)
        var db = RocksDB.open(config.opts, config.dbDir)
        db.createColumnFamily(ColumnFamilyDescriptor("new_cf".toByteArray()))
        db.close()

        assertEquals(listOf("default", "new_cf"), config.listColumnFamilies().map { desc -> String(desc.name) })

        val cfHandles: MutableList<ColumnFamilyHandle> = mutableListOf()
        db = RocksDB.open(config.dbOpts, config.dbDir, config.listColumnFamilies(), cfHandles)
        assertEquals(2, cfHandles.size)
        db.close()
    }

    @Test
    fun kvStoreTest() {
        val dbPath = "build/kvstore/test.db"
        File(dbPath).deleteRecursively()
        File(dbPath).mkdirs()

        val config = RocksDbConfig(dbDir = dbPath)
        val db = RocksDB.open(config.opts, config.dbDir)
        val provider = RocksDbProvider(config, db, mutableListOf())

        val store = KvStore(provider, jacksonObjectMapper())
        val aggregationUnit = "test"

        val key1 = ProcessingKey(aggregationUnit)
        val message1 = ProcessingMessage(key1, "message1")
        store.put(message1)

        val key2 = ProcessingKey(aggregationUnit)
        val message2 = ProcessingMessage(key2, "message2")
        store.put(message2)

        assertEquals(message1, store.get(key1, ProcessingMessage::class.java))
        assertEquals(message2, store.get(key2, ProcessingMessage::class.java))

        val allMessages = store.findAll(aggregationUnit)
        assertEquals(2, allMessages.size)

        val empty = store.findAll("not_exists_column_family")
        assertEquals(0, empty.size)
        db.close()
    }
}