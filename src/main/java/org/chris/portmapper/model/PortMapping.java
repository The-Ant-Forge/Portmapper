/**
 * UPnP PortMapper - A tool for managing port forwardings via UPnP
 * Copyright (C) 2015 Christoph Pirkl <christoph at users.sourceforge.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.chris.portmapper.model;

import java.util.HashMap;
import java.util.Map;

import net.sbbi.upnp.messages.ActionResponse;

/**
 * Represents a port mapping / forwarding on a router. Transient data carrier
 * (not persisted to {@code settings.xml}); only the user-defined
 * {@link PortMappingPreset} graph is.
 *
 * @param protocol the network protocol of the mapping (TCP or UDP).
 * @param remoteHost the remote host the mapping applies to, or {@code null} for any.
 * @param externalPort the external (WAN-facing) port.
 * @param internalClient the internal LAN IP the mapping targets.
 * @param internalPort the internal LAN port the mapping targets.
 * @param description a free-form description.
 * @param enabled whether the mapping is enabled.
 * @param leaseDuration the lease duration in seconds, {@code 0} for "permanent".
 */
public record PortMapping(
        Protocol protocol,
        String remoteHost,
        int externalPort,
        String internalClient,
        int internalPort,
        String description,
        boolean enabled,
        long leaseDuration) {

    public static final String MAPPING_ENTRY_LEASE_DURATION = "NewLeaseDuration";
    public static final String MAPPING_ENTRY_ENABLED = "NewEnabled";
    public static final String MAPPING_ENTRY_REMOTE_HOST = "NewRemoteHost";
    public static final String MAPPING_ENTRY_INTERNAL_CLIENT = "NewInternalClient";
    public static final String MAPPING_ENTRY_PORT_MAPPING_DESCRIPTION = "NewPortMappingDescription";
    public static final String MAPPING_ENTRY_PROTOCOL = "NewProtocol";
    public static final String MAPPING_ENTRY_INTERNAL_PORT = "NewInternalPort";
    public static final String MAPPING_ENTRY_EXTERNAL_PORT = "NewExternalPort";

    private static final long DEFAULT_LEASE_DURATION = 0;

    /**
     * Convenience constructor that defaults {@code enabled=true} and the lease
     * duration to {@value #DEFAULT_LEASE_DURATION}. Most callers in the codebase
     * use this form; the canonical 8-arg constructor is for callers that need to
     * encode the full SBBI/UPnP-IGD response fields.
     */
    public PortMapping(final Protocol protocol, final String remoteHost, final int externalPort,
            final String internalClient, final int internalPort, final String description) {
        this(protocol, remoteHost, externalPort, internalClient, internalPort, description, true,
                DEFAULT_LEASE_DURATION);
    }

    /**
     * Build a {@link PortMapping} from an SBBI {@link ActionResponse}. The SBBI
     * library returns mapping data as an untyped name/value bag keyed by the
     * {@code MAPPING_ENTRY_*} constants.
     *
     * @param response the action response returned by the SBBI library.
     * @return a populated {@code PortMapping}.
     */
    public static PortMapping create(final ActionResponse response) {
        final Map<String, String> values = new HashMap<>();
        for (final Object argObj : response.getOutActionArgumentNames()) {
            final String argName = (String) argObj;
            values.put(argName, response.getOutActionArgumentValue(argName));
        }

        final int externalPort = Integer.parseInt(values.get(MAPPING_ENTRY_EXTERNAL_PORT));
        final int internalPort = Integer.parseInt(values.get(MAPPING_ENTRY_INTERNAL_PORT));
        final String protocolString = values.get(MAPPING_ENTRY_PROTOCOL);
        final Protocol protocol = "TCP".equalsIgnoreCase(protocolString) ? Protocol.TCP : Protocol.UDP;
        final String description = values.get(MAPPING_ENTRY_PORT_MAPPING_DESCRIPTION);
        final String internalClient = values.get(MAPPING_ENTRY_INTERNAL_CLIENT);
        final String remoteHost = values.get(MAPPING_ENTRY_REMOTE_HOST);
        final String enabledString = values.get(MAPPING_ENTRY_ENABLED);
        final boolean enabled = "1".equals(enabledString);
        final long leaseDuration = Long.parseLong(values.get(MAPPING_ENTRY_LEASE_DURATION));
        return new PortMapping(protocol, remoteHost, externalPort, internalClient, internalPort, description,
                enabled, leaseDuration);
    }

    /**
     * @return a human-readable rendering of every field, used by the in-app log.
     */
    public String getCompleteDescription() {
        final StringBuilder b = new StringBuilder();
        b.append(protocol);
        b.append(" ");
        if (remoteHost != null) {
            b.append(remoteHost);
        }
        b.append(":");
        b.append(externalPort);
        b.append(" -> ");
        b.append(internalClient);
        b.append(":");
        b.append(internalPort);
        b.append(" ");
        b.append(enabled ? "enabled" : "not enabled");
        b.append(" ");
        b.append(description);
        return b.toString();
    }

    /** {@inheritDoc} — overridden from the record default to return just the description. */
    @Override
    public String toString() {
        return description;
    }
}
