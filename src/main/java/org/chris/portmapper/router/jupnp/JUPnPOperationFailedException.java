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
package org.chris.portmapper.router.jupnp;

import org.jupnp.model.message.control.IncomingActionResponseMessage;

public class JUPnPOperationFailedException extends JUPnPRouterException {

    private static final long serialVersionUID = 1L;
    private final transient IncomingActionResponseMessage response;

    public JUPnPOperationFailedException(final String message, final IncomingActionResponseMessage response) {
        super(message);
        if (!response.getOperation().isFailed()) {
            throw new IllegalArgumentException("Operation was succesful");
        }
        this.response = response;
    }

    public IncomingActionResponseMessage getResponse() {
        return response;
    }
}
