package org.corfudb.universe.api.deployment.vm;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.corfudb.universe.api.deployment.vm.VmParams.VmName;
import org.corfudb.universe.api.universe.Universe;
import org.corfudb.universe.util.IpAddress;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Represents the parameters for constructing a VM {@link Universe}.
 */
@Builder
@Getter
@ToString
@EqualsAndHashCode
public class VmUniverseParams {

    /**
     * Default https://10.173.65.98/sdk
     */
    @NonNull
    private final String vsphereUrl;

    /**
     * Default "10.172.208.208"
     */
    @NonNull
    private final List<String> vsphereHost;

    @NonNull
    private final VmCredentialsParams credentials;

    @NonNull
    private final String templateVmName;

    @NonNull
    private final ConcurrentMap<VmName, IpAddress> vmIpAddresses;
    @NonNull
    private final String domainName = "eng.vmware.com";
    @NonNull
    private final String timeZone = "America/Los_Angeles";
    @NonNull
    private final String[] dnsServers = new String[]{"10.172.40.1", "10.172.40.2"};
    @NonNull
    private final String[] domainSuffixes = new String[]{"eng.vmware.com", "vmware.com"};
    @NonNull
    private final String[] gateways = new String[]{"10.172.211.253"};
    @NonNull
    private final String subnet = "255.255.255.0";
    @NonNull
    private final Duration readinessTimeout = Duration.ofSeconds(3);

    public VmUniverseParams updateIpAddress(VmName vmName, IpAddress ipAddress) {
        vmIpAddresses.put(vmName, ipAddress);
        return this;
    }

    /**
     * VM credentials stored in a property file (vm.credentials.properties)
     * The file format:
     * <pre>
     * vsphere.username=vSphereUser
     * vsphere.password=vSpherePassword
     *
     * vm.username=vmPass
     * vm.password=vmUser
     * </pre>
     */
    @Builder
    @Getter
    public static class VmCredentialsParams {
        @NonNull
        private final Credentials vmCredentials;
        @NonNull
        private final Credentials vsphereCredentials;
    }

    /**
     * Security credentials
     */
    @Builder
    @Getter
    public static class Credentials {
        @NonNull
        private final String username;
        @NonNull
        private final String password;
    }
}
