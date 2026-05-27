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

import java.util.Collection;
import java.util.LinkedList;

import org.chris.portmapper.model.PortMapping;
import org.chris.portmapper.router.jupnp.action.ActionService;
import org.chris.portmapper.router.jupnp.action.GetPortMappingEntryAction;
import org.jupnp.model.message.control.IncomingActionResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enumerates {@link PortMapping} entries from a router by invoking the jUPnP
 * {@link ActionService} bound to its WAN connection service. Stops cleanly on
 * the UPnP terminal error codes (713 / 402 / 714 / 899).
 */
class JUPnPPortMappingExtractor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Collection<PortMapping> mappings;
    private boolean moreEntries;
    private int currentMappingNumber;

    /**
     * The maximum number of port mappings that we will try to retrieve from the router.
     */
    private final int maxNumPortMappings;
    private final ActionService actionService;

    JUPnPPortMappingExtractor(final ActionService actionService, final int maxNumPortMappings) {
        this.actionService = actionService;
        this.maxNumPortMappings = maxNumPortMappings;
        this.mappings = new LinkedList<>();
        this.moreEntries = true;
        this.currentMappingNumber = 0;
    }

    public Collection<PortMapping> getPortMappings() {

        /*
         * This is a little trick to get all port mappings. There is a method that gets the number of available port
         * mappings (getNatMappingsCount()), but it seems, that this method just tries to get all port mappings and
         * checks, if an error is returned.
         *
         * In order to speed this up, we will do the same here, but stop, when the first exception is thrown.
         */

        while (morePortMappingsAvailable()) {
            logger.debug("Getting port mapping with entry number {}...", currentMappingNumber);

            try {
                final PortMapping portMapping = actionService
                        .run(new GetPortMappingEntryAction(actionService.getService(), currentMappingNumber));
                mappings.add(portMapping);
            } catch (final JUPnPOperationFailedException e) {
                handleFailureResponse(e.getResponse());
            }
            currentMappingNumber++;
        }

        checkMaxNumPortMappingsReached();

        return mappings;
    }

    /**
     * Check, if the max number of entries is reached and print a warning message.
     */
    private void checkMaxNumPortMappingsReached() {
        if (currentMappingNumber == maxNumPortMappings) {
            logger.warn(
                    "Reached max number of port mappings to get ({}). Perhaps not all port mappings where retrieved.",
                    maxNumPortMappings);
        }
    }

    private boolean morePortMappingsAvailable() {
        return moreEntries && currentMappingNumber < maxNumPortMappings;
    }

    private void handleFailureResponse(final IncomingActionResponseMessage incomingActionResponseMessage) {
        if (isNoMoreMappingsException(incomingActionResponseMessage)) {
            moreEntries = false;
            logger.debug("Got no port mapping for entry number {} (status: {}). Stop getting more entries.",
                    currentMappingNumber, incomingActionResponseMessage.getOperation().getStatusMessage());
        } else {
            moreEntries = false;
            logger.info(
                    "Got error response when fetching port mapping for entry number {}: '{}'. Stop getting more entries.",
                    currentMappingNumber, incomingActionResponseMessage);
        }
    }

    /**
     * Check whether the response's error code is a terminal one — i.e. the router is telling us
     * we've run off the end of its port-mapping array and we should stop iterating.
     *
     * <p>Recognised terminal codes (gathered empirically across multiple router firmwares):
     * <ul>
     * <li>{@code 713} — SpecifiedArrayIndexInvalid (the UPnP-IGD standard).</li>
     * <li>{@code 714} — NoSuchEntryInArray (some firmwares return this instead of 713).</li>
     * <li>{@code 402} — Invalid Args (DD-WRT, older TP-Link TL-R460 firmwares).</li>
     * <li>{@code 899} — generic SOAP fault catch-all returned by some old routers (ActionTec
     *     MI424-WR, Thomson TWG850-4U) when the array is exhausted.</li>
     * </ul>
     *
     * @param incomingActionResponseMessage the response to check.
     * @return {@code true} if the response indicates "no more mappings"; {@code false} for any
     *         other status (including success).
     */
    private boolean isNoMoreMappingsException(final IncomingActionResponseMessage incomingActionResponseMessage) {
        final int errorCode = incomingActionResponseMessage.getOperation().getStatusCode();
        switch (errorCode) {
        case 713:
        case 714:
        case 402:
        case 899:
            return true;

        default:
            return false;
        }
    }
}
