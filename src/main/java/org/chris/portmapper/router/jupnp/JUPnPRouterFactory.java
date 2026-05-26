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

import static java.util.stream.Collectors.*;

import java.time.Duration;
import java.util.List;

import org.chris.portmapper.PortMapperApp;
import org.chris.portmapper.router.AbstractRouterFactory;
import org.chris.portmapper.router.IRouter;
import org.chris.portmapper.router.RouterException;
import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.message.header.UDADeviceTypeHeader;
import org.jupnp.model.message.header.UpnpHeader;
import org.jupnp.model.meta.RemoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JUPnPRouterFactory extends AbstractRouterFactory {

    private static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(3);
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public JUPnPRouterFactory(final PortMapperApp app) {
        super(app, "JUPnP lib");
    }

    @Override
    protected List<IRouter> findRoutersInternal() throws RouterException {
        final UpnpServiceConfiguration config = new DefaultUpnpServiceConfiguration();
        final JUPnPRegistryListener clingRegistryListener = new JUPnPRegistryListener();
        final UpnpService upnpService = new UpnpServiceImpl(config);
        // jUPnP creates the registry inside startup(); calling getRegistry() before
        // startup() returns null. Cling's old (config, listener) constructor used to
        // do both atomically, so the original code's ordering carried over wrong.
        upnpService.startup();
        upnpService.getRegistry().addListener(clingRegistryListener);
        shutdownServiceOnExit(upnpService);

        final UpnpHeader<?> searchType = new UDADeviceTypeHeader(JUPnPRegistryListener.IGD_DEVICE_TYPE);
        log.info("Start searching {} for device type {}", DISCOVERY_TIMEOUT, searchType);
        upnpService.getControlPoint().search(searchType, (int) DISCOVERY_TIMEOUT.toSeconds());
        return clingRegistryListener
                .waitForServiceFound(DISCOVERY_TIMEOUT) //
                .map(service -> (RemoteService) service)
                .map(service -> createRouter(service, upnpService)) //
                .collect(toList());
    }

    private JUPnPRouter createRouter(final RemoteService service, final UpnpService upnpService) {
        return new JUPnPRouter(service, upnpService.getRegistry(), upnpService.getControlPoint());
    }

    private void shutdownServiceOnExit(final UpnpService upnpService) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.debug("Shutdown upnp service");
            upnpService.shutdown();
        }, "jupnp-shutdown"));
    }

    @Override
    protected IRouter connect(final String locationUrl) throws RouterException {
        throw new UnsupportedOperationException(
                "Direct connection via location URL is not supported for JUPnP library.");
    }
}
