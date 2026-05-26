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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A named, persisted preset describing a set of port mappings that the user
 * can apply against the currently-connected router in one click. Persisted to
 * {@code settings.xml} via {@code XMLEncoder} using the record's canonical
 * constructor.
 *
 * <p>The previous {@code isNew} flag and {@code save(Settings)} method are gone
 * in the records refactor — those were UI/lifecycle concerns, not data, and now
 * live in {@code EditPresetDialog} which decides "add or replace" against the
 * settings list.
 *
 * @param description user-facing description / label.
 * @param internalClient internal LAN IP, or {@code null} to use the current
 *                       localhost address.
 * @param remoteHost remote host the mappings apply to, or {@code null} for any.
 * @param ports the list of single port mappings in this preset.
 */
public record PortMappingPreset(
        String description,
        String internalClient,
        String remoteHost,
        @SuppressWarnings("serial") List<SinglePortMapping> ports) implements Serializable {

    private static final long serialVersionUID = 3749136884938395765L;

    /** Build an empty preset (used as the seed for "Create new preset" in the GUI). */
    public static PortMappingPreset empty() {
        return new PortMappingPreset(null, null, null, new LinkedList<>());
    }

    @Override
    public String toString() {
        return description;
    }

    /**
     * Expand this preset into a list of concrete {@link PortMapping} instances
     * for the currently-connected router.
     *
     * @param localhost the local host address; required if this preset uses
     *                  the localhost-as-internal-client mode.
     * @return one {@code PortMapping} per port in this preset.
     * @throws IllegalArgumentException if the preset wants localhost and the
     *         caller didn't supply one.
     */
    public List<PortMapping> getPortMappings(final String localhost) {
        if (useLocalhostAsInternalClient() && (localhost == null || localhost.length() == 0)) {
            throw new IllegalArgumentException("Got invalid localhost and internal host is not given.");
        }

        final List<PortMapping> allPortMappings = new ArrayList<>(this.ports.size());
        for (final SinglePortMapping port : this.ports) {
            final String internalClientName = useLocalhostAsInternalClient() ? localhost : this.internalClient;
            allPortMappings.add(new PortMapping(port.protocol(), remoteHost, port.externalPort(),
                    internalClientName, port.internalPort(), description));
        }
        return allPortMappings;
    }

    /**
     * @return a verbose human-readable rendering of this preset including the
     *         remote host, internal client, description, and every port mapping.
     *         Used by the in-app log on settings save.
     */
    public String getCompleteDescription() {
        final StringBuilder b = new StringBuilder();
        b.append(description);
        b.append(" [");
        if (remoteHost != null) {
            b.append(remoteHost);
        }
        b.append(" -> ");
        b.append(useLocalhostAsInternalClient() ? "<localhost>" : internalClient);
        b.append("]");
        for (final SinglePortMapping port : ports) {
            b.append(" ");
            b.append(port.protocol());
            b.append(" ");
            b.append(port.externalPort());
            b.append("->");
            b.append(port.internalPort());
        }
        return b.toString();
    }

    /**
     * @return {@code true} when the preset wants the current localhost address
     *         resolved at apply time, rather than the preset's stored
     *         {@code internalClient}.
     */
    public boolean useLocalhostAsInternalClient() {
        return internalClient == null || internalClient.length() == 0;
    }
}
