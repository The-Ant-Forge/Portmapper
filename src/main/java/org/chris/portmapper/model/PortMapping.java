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

    private static final long DEFAULT_LEASE_DURATION = 0;

    /**
     * Convenience constructor that defaults {@code enabled=true} and the lease
     * duration to {@value #DEFAULT_LEASE_DURATION}. Most callers in the codebase
     * use this form; the canonical 8-arg constructor is for callers that need to
     * encode the full UPnP-IGD response fields.
     */
    public PortMapping(final Protocol protocol, final String remoteHost, final int externalPort,
            final String internalClient, final int internalPort, final String description) {
        this(protocol, remoteHost, externalPort, internalClient, internalPort, description, true,
                DEFAULT_LEASE_DURATION);
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
