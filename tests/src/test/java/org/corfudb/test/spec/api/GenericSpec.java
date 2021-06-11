package org.corfudb.test.spec.api;

import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.TxnContext;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.test.util.UfoUtils;

import java.util.function.BiConsumer;

public interface GenericSpec {

    class SpecHelper {
        private final CorfuStore corfuStore;
        private final UfoUtils utils;

        // Define a namespace for the table.
        private final String namespace = "manager";

        // Define table name
        private final ManagedResources metadata;

        public SpecHelper(CorfuRuntime runtime, String tableName) {
            this.corfuStore = new CorfuStore(runtime);

            this.metadata = ManagedResources.newBuilder()
                    .setCreateUser("MrProto")
                    .build();

            this.utils = new UfoUtils(corfuStore, namespace, tableName, metadata);
        }

        public void transactional(BiConsumer<UfoUtils, TxnContext> action) {
            try (TxnContext txn = corfuStore.txn(namespace)) {
                action.accept(utils, txn);
                txn.commit();
            }
        }
    }
}
