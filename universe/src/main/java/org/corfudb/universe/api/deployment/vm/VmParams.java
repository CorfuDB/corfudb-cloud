package org.corfudb.universe.api.deployment.vm;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Vm specific set of parameters
 */
@Builder
@Getter
@EqualsAndHashCode
@ToString
public class VmParams {

    @NonNull
    private final VmName vmName;

    @Builder
    @EqualsAndHashCode
    @Getter
    public static class VmName implements Comparable<VmName> {
        /**
         * Vm name in a vSphere cluster
         */
        @NonNull
        private final String name;

        /**
         * Vm index in a vm.properties config
         */
        @NonNull
        private final Integer index;

        @Override
        public int compareTo(VmName other) {
            return name.compareTo(other.name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
