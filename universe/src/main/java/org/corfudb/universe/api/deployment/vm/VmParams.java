package org.corfudb.universe.api.deployment.vm;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.node.Node.NodeParams;
import org.corfudb.universe.util.IpAddress;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Vm specific set of parameters
 */
@Builder
@Getter
@EqualsAndHashCode
@ToString
public class VmParams<P extends NodeParams> implements DeploymentParams<P> {

    @NonNull
    private final VmName vmName;

    @NonNull
    private final VmParams.VsphereParams vsphereParams;

    @NonNull
    private final P applicationParams;

    @Builder
    @Getter
    public static class VsphereParams {
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
        private final String networkName;

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

        public VsphereParams updateIpAddress(VmName vmName, IpAddress ipAddress) {
            vmIpAddresses.put(vmName, ipAddress);
            return this;
        }
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
