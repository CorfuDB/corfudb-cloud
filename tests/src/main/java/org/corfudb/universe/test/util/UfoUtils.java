package org.corfudb.universe.test.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TableOptions;
import org.corfudb.runtime.collections.TxnContext;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.api.universe.UniverseException;
import org.corfudb.universe.test.UniverseConfigurator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public final class UfoUtils {

    private final CorfuStore corfuStore;
    private final String namespace;
    @Getter
    private final String tableName;
    @Getter
    private final Table<IdMessage, EventInfo, ManagedResources> table;
    private final ManagedResources metadata;

    public UfoUtils(CorfuStore corfuStore, String namespace, String tableName, ManagedResources metadata) {
        this.corfuStore = corfuStore;
        this.namespace = namespace;
        this.tableName = tableName;
        this.table = createTable();
        this.metadata = metadata;
    }

    enum EventType {
        EVENT, UPDATE
    }

    /**
     * Create & Register the table.
     * This is required to initialize the table for the current corfu client.
     */
    public Table<IdMessage, EventInfo, ManagedResources> createTable() throws UniverseException {

        try {
            return corfuStore.openTable(
                    namespace,
                    tableName,
                    IdMessage.class,
                    EventInfo.class,
                    ManagedResources.class,
                    // TableOptions includes option to choose - Memory/Disk based corfu table.
                    TableOptions.builder().build());
        } catch (Exception e) {
            throw new UniverseException("Can't create corfu table", e);
        }
    }

    /**
     * Generate events and update a table
     *
     * @param start    start
     * @param end      end
     * @param uuids    list of ids
     * @param events   list of events
     * @param txn      transaction builder
     * @param isUpdate whether to update the table or not
     */
    public void generateData(
            int start, int end,
            List<IdMessage> uuids, List<EventInfo> events, TxnContext txn, boolean isUpdate) {

        String eventName = EventType.EVENT.name();
        for (int i = start; i < end; i++) {
            byte[] bytes = Integer.toString(i).getBytes(StandardCharsets.UTF_8);
            UUID uuid = UUID.nameUUIDFromBytes(bytes);
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

            txn.putRecord(table, uuids.get(i), events.get(i), metadata);
        }
    }

    /**
     * Verifies that a table has expected count of rows
     *
     * @param expectedRowCount expected number of rows
     */
    public void verifyTableRowCount(TxnContext txn, int expectedRowCount) {
        log.info(" verify table using row count ");
        assertThat(txn.getTable(tableName).count()).isEqualTo(expectedRowCount);
    }

    /**
     * Verify corfu table
     *
     * @param start          start
     * @param end            end
     * @param updatedContent updated content
     */
    public void verifyTableData(TxnContext txn, int start, int end, boolean updatedContent) {
        String eventName = EventType.EVENT.name();
        if (updatedContent) {
            eventName = EventType.UPDATE.name();
        }
        for (int key = start; key < end; key++) {
            byte[] bytes = Integer.toString(key).getBytes(StandardCharsets.UTF_8);
            UUID uuid = UUID.nameUUIDFromBytes(bytes);
            IdMessage keyValue1 = IdMessage.newBuilder()
                    .setMsb(uuid.getMostSignificantBits())
                    .setLsb(uuid.getLeastSignificantBits())
                    .build();

            EventInfo expectedValue = EventInfo.newBuilder()
                    .setId(key)
                    .setName(eventName + key)
                    .setEventTime(key)
                    .build();

            assertThat(txn.getRecord(tableName, keyValue1).getPayload()).isEqualTo(expectedValue);
        }
    }

    /**
     * Clear corfu table and verify that it is emty
     *
     * @param txnContext transaction context
     */
    public void clearTableAndVerify(TxnContext txnContext) {

        //Delete all the table entries
        log.info("Clearing all the entries present in table");
        table.clear();

        log.info("Verify table entries are cleared");
        assertThat(txnContext.getTable(tableName).count()).isEqualTo(0);
    }

    /**
     * Whether cleaning of test data enabled or not
     *
     * @return if clean up is enabled
     */
    public boolean cleanTestDataEnabled() {
        // Returns value for "test.data.clean" key from universe-tests.properties
        Properties props = UniverseConfigurator.getCfg();
        return Boolean.parseBoolean(props.getProperty("test.data.clean"));
    }
}
