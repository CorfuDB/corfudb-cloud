package org.corfudb.cloud.infrastructure.integration.processing


import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Test
import org.rocksdb.Options
import org.rocksdb.RocksDB
import kotlin.test.assertEquals

class KvStoreTest {

    @Test
    fun helloWorldReturnsPersonalizedMessage() {
        RocksDB.loadLibrary()
        val db = RocksDB.open(Options().setCreateIfMissing(true), "processing.db")
        val store = KvStore(db, jacksonObjectMapper())

        val key1 = ProcessingKey("test")

        store.put(key1, "message1")
        val key2 = ProcessingKey("test")
        store.put(key2, "message2")

        assertEquals("yay", store.get(key1, String::class.java))
        assertEquals("yay", store.get(key2, String::class.java))
    }
}