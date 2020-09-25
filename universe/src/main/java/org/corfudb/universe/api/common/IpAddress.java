package org.corfudb.universe.api.common;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

@Builder
@EqualsAndHashCode
public class IpAddress {

    /**
     * Ip address or dns name
     */
    @Getter
    @NonNull
    private final String ip;

    @Override
    public String toString() {
        return ip;
    }
}
