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
package org.chris.portmapper.router;

import static java.util.Arrays.*;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;

import org.chris.portmapper.PortMapperApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The base class for all router factories.
 */
public abstract class AbstractRouterFactory {

    private static final String LOCATION_URL_SYSTEM_PROPERTY = "portmapper.locationUrl";

    private static final Logger staticLogger = LoggerFactory.getLogger(AbstractRouterFactory.class);

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final PortMapperApp app;

    private final String name;

    protected AbstractRouterFactory(final PortMapperApp app, final String name) {
        this.app = app;
        this.name = name;
    }

    /**
     * Resolve a concrete {@code AbstractRouterFactory} subclass by FQCN and
     * instantiate it via its {@code public RouterFactory(PortMapperApp)}
     * constructor. Shared by the GUI ({@code PortMapperApp.connectRouter}) and
     * the CLI ({@code PortMapperCli.connect}) paths; the duplicated reflective
     * dispatch they used to carry was code-review finding F9.
     *
     * @param className the fully-qualified class name of the factory subclass.
     * @param owner the {@code PortMapperApp} to pass to the factory constructor;
     *        the CLI path supplies a throwaway instance because the constructor
     *        contract requires it.
     * @return the instantiated factory.
     * @throws RouterException if the class can't be loaded, has no matching
     *         constructor, or the constructor invocation fails.
     */
    @SuppressWarnings("unchecked")
    public static AbstractRouterFactory create(final String className, final PortMapperApp owner)
            throws RouterException {
        staticLogger.info("Creating router factory for class {}", className);
        final Class<? extends AbstractRouterFactory> clazz;
        try {
            clazz = (Class<? extends AbstractRouterFactory>) Class.forName(className);
        } catch (final ClassNotFoundException e) {
            throw new RouterException("Did not find router factory class for name " + className, e);
        }
        final Constructor<? extends AbstractRouterFactory> constructor;
        try {
            constructor = clazz.getConstructor(PortMapperApp.class);
        } catch (final NoSuchMethodException | SecurityException e) {
            throw new RouterException("Could not find constructor of " + clazz.getName(), e);
        }
        try {
            return constructor.newInstance(owner);
        } catch (final ReflectiveOperationException e) {
            throw new RouterException("Could not create a router factory using constructor " + constructor, e);
        }
    }

    /**
     * Get the name of the router factory that can be displayed to the user.
     *
     * @return the name of the router factory that can be displayed to the user.
     */
    public String getName() {
        return name;
    }

    public List<IRouter> findRouters() throws RouterException {
        final String locationUrl = System.getProperty(LOCATION_URL_SYSTEM_PROPERTY);
        if (locationUrl == null) {
            logger.debug("System property '{}' not defined: discover routers automatically.",
                    LOCATION_URL_SYSTEM_PROPERTY);
            return findRoutersInternal();
        }
        logger.info("Trying to connect using location url {}", locationUrl);
        return asList(connect(locationUrl));
    }

    /**
     * Search for routers on the network.
     *
     * @return the found router or an empty {@link Collection} if no router was found.
     * @throws RouterException
     *             if something goes wrong during discovery.
     */
    protected abstract List<IRouter> findRoutersInternal() throws RouterException;

    /**
     * Directly connect to a router using a location url like <code>http://192.168.179.1:49000/igddesc.xml</code>.
     *
     * @param locationUrl
     *            a location url
     * @return a router if the connection was successful.
     * @throws RouterException
     *             if something goes wrong during connection.
     */
    protected abstract IRouter connect(final String locationUrl) throws RouterException;

    @Override
    public String toString() {
        return name;
    }
}
