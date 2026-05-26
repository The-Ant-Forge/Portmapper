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

/**
 * A single port mapping entry inside a {@link PortMappingPreset}: protocol
 * (TCP/UDP) plus an internal and external port. Persisted to {@code settings.xml}
 * via {@code XMLEncoder} using the record's canonical constructor.
 *
 * @param protocol the network protocol of this mapping.
 * @param internalPort the internal port.
 * @param externalPort the external port.
 */
public record SinglePortMapping(Protocol protocol, int internalPort, int externalPort) implements Serializable {

    private static final long serialVersionUID = 7458514232916039775L;

    /** Default placeholder mapping used when a fresh row is added in the GUI. */
    public static SinglePortMapping defaults() {
        return new SinglePortMapping(Protocol.TCP, 1, 1);
    }
}
