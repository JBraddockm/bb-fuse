package com.jbraddockm.core.tunnel;

/**
 * A hold on a device's tunnel, returned by {@link TunnelBrokerClient#acquire}. The {@code token}
 * releases it; {@code ula} is the host a consumer connects to and {@code innerIpv4} is the SMB
 * host. {@code hostUla} is our own tunnel ULA — the bind address for a host-side service the device
 * reaches over the tunnel. {@code guid}/{@code pin} are present only once the device has been
 * identified.
 *
 * @param token opaque lease id, passed back to release the hold
 * @param ula the device's tunnel ULA (what bb-link/Blend connect to)
 * @param hostUla our (the host's) tunnel ULA — the bind address for a host-side service (e.g. the
 *     rimdav file server on {@code [hostUla]:4487}), or {@code null} if the broker did not report
 *     it
 * @param innerIpv4 the device's inner IPv4 on the tunnel (the SMB host), or {@code null}
 * @param guid the device GUID if known, else {@code null}
 * @param pin the device PIN if known, else {@code null}
 * @param refs how many consumers now hold this device's tunnel
 */
public record BrokerLease(
    String token,
    String ula,
    String hostUla,
    String innerIpv4,
    String guid,
    String pin,
    int refs) {}
