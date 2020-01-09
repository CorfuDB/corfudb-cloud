package com.vmware.corfudb.universe.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.vmware.corfudb.universe.UniverseConfigurator;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TableOptions;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Slf4j
public class UfoUtils {


    private UfoUtils() {
        //prevent creating instances
    }

    enum EventType {
        EVENT, UPDATE
    }

    /**
     * Create & Register the table.
     * This is required to initialize the table for the current corfu client.
     *
     * @param corfuStore corfu CorfuStore.
     * @param namespace  corfu namespace
     * @param tableName  corfu table-name
     */
    public static Table<IdMessage, EventInfo, ManagedResources>
    createTable(CorfuStore corfuStore, String namespace, String tableName)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return corfuStore.openTable(
                namespace,
                tableName,
                IdMessage.class,
                EventInfo.class,
                ManagedResources.class,
                // TableOptions includes option to choose - Memory/Disk based corfu table.
                TableOptions.builder().build());
    }

    public static void generateDataAndCommit(
            int start, int end, String tableName, List<IdMessage> uuids,
            List<EventInfo> events, TxBuilder tx, ManagedResources metadata, boolean isUpdate) {


        String eventName = EventType.EVENT.name();
        for (int i = start; i < end; i++) {
            UUID uuid = UUID.nameUUIDFromBytes(Integer.toString(i).getBytes());
            IdMessage uuidMsg = IdMessage.newBuilder()
                    .setMsb(uuid.getMostSignificantBits())
                    .setLsb(uuid.getLeastSignificantBits())
                    .build();

            if (isUpdate) {
                uuids.set(i, uuidMsg);

                eventName = EventType.UPDATE.name();
                events.set(i, EventInfo.newBuilder()
                        .setId(i)
                        .setName(eventName + i)
                        .setEventTime(i)
                        .build());
            } else {
                uuids.add(uuidMsg);
                events.add(EventInfo.newBuilder()
                        .setId(i)
                        .setName(eventName + i)
                        .setEventTime(i)
                        .build());
            }

            tx.update(tableName, uuids.get(i), events.get(i), metadata);
        }

        tx.commit();
    }

    public static void verifyTableRowCount(
            CorfuStore corfuStore, String namespace, String tableName, int expectedRowCount) {

        Query q = corfuStore.query(namespace);

        log.info(" verify table using row count ");
        assertThat(q.count(tableName)).isEqualTo(expectedRowCount);
    }

    public static void verifyTableData(
            CorfuStore corfuStore, int start, int end, String namespace, String tableName,
            boolean updatedContent) {

        Query q = corfuStore.query(namespace);
        String eventName = EventType.EVENT.name();
        if (updatedContent) {
            eventName = EventType.UPDATE.name();
        }
        for (int key = start; key < end; key++) {
            UUID uuid = UUID.nameUUIDFromBytes(Integer.toString(key).getBytes());
            IdMessage KeyValue1 = IdMessage.newBuilder()
                    .setMsb(uuid.getMostSignificantBits())
                    .setLsb(uuid.getLeastSignificantBits())
                    .build();

            EventInfo expectedValue = EventInfo.newBuilder()
                    .setId(key)
                    .setName(eventName + key)
                    .setEventTime(key)
                    .build();

            assertThat(q.getRecord(tableName, KeyValue1).getPayload()).isEqualTo(expectedValue);
        }
    }

    public static void clearTableAndVerify(
            Table<IdMessage, EventInfo, ManagedResources> table, String tableName, Query queryObj) {

        //Delete all the table entries
        log.info("Clearing all the entries present in table");
        table.clear();

        log.info("Verify table entries are cleared");
        assertThat(queryObj.count(tableName)).isEqualTo(0);
    }

    public static boolean cleanTestDataEnabled() {
        // Returns value for "test.data.clean" key from universe-tests.properties
        Properties props = UniverseConfigurator.getConfig();
        return Boolean.parseBoolean(props.getProperty("test.data.clean"));
    }
}
