package org.corfudb.cloud.infrastructure.integration.processing


import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.corfudb.cloud.infrastructure.integration.kv.*
import org.junit.Test
import org.rocksdb.RocksDB
import java.io.File
import kotlin.test.assertEquals

class KvStoreTest {

    @Test
    fun kvStoreTEst() {
        RocksDB.loadLibrary()
        File("test.db").deleteRecursively()

        val config = RocksDbConfig(dbDir = "test.db")
        val db = RocksDB.open(config.opts, config.dbDir)
        val provider = RocksDbProvider(config, db, mutableListOf())

        val store = KvStore(provider, jacksonObjectMapper())
        val aggregationUnit = "test"

        val key1 = ProcessingKey(aggregationUnit)
        val message1 = ProcessingMessage(key1, "message1")
        store.put(key1, message1)

        val key2 = ProcessingKey(aggregationUnit)
        val message2 = ProcessingMessage(key2, "message2")
        store.put(key2, message2)

        assertEquals(message1, store.get(key1, ProcessingMessage::class.java))
        assertEquals(message2, store.get(key2, ProcessingMessage::class.java))

        val allMessages = store.findAll(aggregationUnit)
        assertEquals(2, allMessages.size)

        val empty = store.findAll("not_exists_column_family")
        assertEquals(0, empty.size)
    }
}