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
package org.chris.portmapper;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.chris.portmapper.gui.PortMapperView;
import org.chris.portmapper.logging.LogMessageListener;
import org.chris.portmapper.logging.LogMessageOutputStream;
import org.chris.portmapper.logging.LogbackConfiguration;
import org.chris.portmapper.model.PortMappingPreset;
import org.chris.portmapper.router.AbstractRouterFactory;
import org.chris.portmapper.router.IRouter;
import org.chris.portmapper.router.RouterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application bootstrap and lifecycle. Owns the {@link PortMapperView} (and via it the
 * main {@link JFrame}), the {@link Settings} model, the {@link SettingsStorage} for
 * persistence, and the current {@link IRouter} connection.
 *
 * <p>Replaces the BSAF {@code SingleFrameApplication} base class. The previous
 * lifecycle hooks ({@code startup()}, {@code shutdown()}) and {@code ExitListener}
 * chain are gone; bootstrap is an explicit {@link #startup()} call from
 * {@link PortMapperCli#startGui}, and cleanup runs as a JVM shutdown hook.
 * Closing the main frame uses {@link javax.swing.WindowConstants#EXIT_ON_CLOSE}
 * which triggers shutdown hooks via {@code System.exit(0)}.
 */
public class PortMapperApp {

    /** The file name for the settings file. */
    private static final String SETTINGS_FILENAME = "settings.xml";

    private static final Logger logger = LoggerFactory.getLogger(PortMapperApp.class);

    private IRouter router;
    private Settings settings;
    private SettingsStorage storage;
    private PortMapperView view;
    private final LogMessageOutputStream logMessageOutputStream = new LogMessageOutputStream();
    private final LogbackConfiguration logbackConfig = new LogbackConfiguration();

    /**
     * Run the application: configure logging, load settings, apply look-and-feel,
     * schedule GUI construction on the EDT, register a shutdown hook for cleanup.
     * Returns once the GUI launch has been scheduled; the AWT event thread keeps
     * the JVM alive thereafter.
     */
    public void startup() {
        logbackConfig.registerOutputStream(logMessageOutputStream);
        initStorage();
        loadSettings();
        applyLookAndFeel();
        SwingUtilities.invokeLater(this::launchGui);
        Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown, "portmapper-shutdown"));
    }

    private void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (final Exception e) {
            // System L&F is best-effort; cross-platform L&F is the JDK fallback if this fails.
            logger.warn("Could not set system look-and-feel; using cross-platform default: {}", e.getMessage());
        }
    }

    private void launchGui() {
        view = new PortMapperView(this);
        view.getFrame().setLocationRelativeTo(null); // center on screen
        view.getFrame().setVisible(true);
        registerSystemMenuHandlers();
    }

    /**
     * JVM shutdown hook: gracefully disconnect any active router and persist the
     * current settings. Runs on whatever thread the JVM chooses, not the EDT —
     * touches no Swing components.
     */
    private void onShutdown() {
        logger.debug("Shutdown hook running");
        disconnectRouter();
        saveSettings();
    }

    private void saveSettings() {
        if (settings == null || storage == null) {
            return;
        }
        logger.debug("Saving settings {} to file {}", settings, SETTINGS_FILENAME);
        if (logger.isTraceEnabled()) {
            for (final PortMappingPreset preset : settings.getPresets()) {
                logger.trace("Saving port mapping {}", preset.getCompleteDescription());
            }
        }
        try {
            storage.save(SETTINGS_FILENAME, settings);
        } catch (final IOException e) {
            logger.warn("Could not save settings to file " + SETTINGS_FILENAME, e);
        }
    }

    /**
     * Wire macOS-style system menu handlers (About, Preferences) to the application.
     * On platforms that do not expose these (Windows, most Linux), {@link Desktop#isSupported}
     * returns {@code false} for the specific actions and the handler is silently skipped, so
     * this method is safe to call unconditionally.
     */
    private void registerSystemMenuHandlers() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        final Desktop desktop = Desktop.getDesktop();
        final PortMapperView v = getView();
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(e -> v.changeSettings());
        }
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(e -> v.showAboutDialog());
        }
    }

    /**
     * Initialise {@link SettingsStorage}, which decides where the application's XML configuration
     * is read from and written to. See {@link SettingsStorage#discover()} for the directory-selection
     * precedence (system property, portable dir, OS default).
     */
    private void initStorage() {
        try {
            storage = SettingsStorage.discover();
        } catch (final IllegalStateException e) {
            logger.error("Could not initialise settings storage: {}", e.getMessage());
            System.exit(1);
            return;
        }
        logger.info("Using configuration directory '{}'.", storage.getDirectory().getAbsolutePath());
    }

    /**
     * Load the application settings from {@link PortMapperApp#SETTINGS_FILENAME} in the storage
     * directory. If the file is missing or can't be parsed, fall back to a fresh {@link Settings}.
     */
    private void loadSettings() {
        logger.debug("Loading settings from file {}", SETTINGS_FILENAME);
        try {
            settings = storage.load(SETTINGS_FILENAME, Settings.class);
        } catch (final IOException e) {
            logger.warn("Could not load settings from file {}; starting fresh.", SETTINGS_FILENAME, e);
        }

        if (settings == null) {
            logger.debug("Settings were not loaded from file {}: create new settings", SETTINGS_FILENAME);
            settings = new Settings();
        } else {
            logger.debug("Got settings {}", settings);
            this.setLogLevel(settings.getLogLevel());
        }
    }

    public void setLogMessageListener(final LogMessageListener listener) {
        this.logMessageOutputStream.registerListener(listener);
    }

    /**
     * Show a modal {@link JDialog}. Replaces BSAF's
     * {@code Application.show(JDialog)} which did similar but went through the
     * BSAF context.
     *
     * @param dialog the dialog to show; should already have its parent set
     *               (typically via {@link #getMainFrame()}).
     */
    public void show(final JDialog dialog) {
        dialog.setVisible(true);
    }

    /** @return the application's main top-level window, or {@code null} if startup hasn't completed yet. */
    public JFrame getMainFrame() {
        return view == null ? null : view.getFrame();
    }

    public PortMapperView getView() {
        return view;
    }

    /**
     * Find and connect to a router. On the EDT-respecting code path (a button click)
     * the worker thread is provided by {@link PortMapperView}'s {@code ConnectTask};
     * the {@link JOptionPane#showInputDialog} call at the bottom (only used when
     * multiple routers are discovered) technically wants the EDT - latent issue
     * inherited from the pre-modernisation code, not addressed in this step.
     *
     * @throws RouterException if the router-factory can't be instantiated or the
     *         discovery step fails.
     */
    public void connectRouter() throws RouterException {
        if (this.router != null) {
            logger.warn("Already connected to router. Cannot create a second connection.");
            return;
        }

        final AbstractRouterFactory routerFactory;
        try {
            routerFactory = createRouterFactory();
        } catch (final RouterException e) {
            logger.error("Could not create router factory: {}", e.getMessage(), e);
            return;
        }
        logger.info("Searching for routers...");

        final Collection<IRouter> foundRouters = routerFactory.findRouters();

        // No routers found
        if (foundRouters == null || foundRouters.isEmpty()) {
            throw new RouterException("Did not find a router");
        }

        // One router found: use it.
        if (foundRouters.size() == 1) {
            router = foundRouters.iterator().next();
            logger.info("Connected to router '{}'", router.getName());
            this.getView().fireConnectionStateChange();
            return;
        }

        // More than one router found: ask user.
        logger.info("Found more than one router (count: {}): ask user.", foundRouters.size());

        final IRouter selectedRouter = (IRouter) JOptionPane.showInputDialog(this.getView().getFrame(),
                Messages.get("messages.select_router.message"),
                Messages.get("messages.select_router.title"), JOptionPane.QUESTION_MESSAGE, null,
                foundRouters.toArray(), null);

        if (selectedRouter == null) {
            logger.info("No router selected.");
            return;
        }

        this.router = selectedRouter;
        this.getView().fireConnectionStateChange();
    }

    private AbstractRouterFactory createRouterFactory() throws RouterException {
        logger.info("Creating router factory for class {}", settings.getRouterFactoryClassName());
        final Class<AbstractRouterFactory> routerFactoryClass = getClassForName(settings.getRouterFactoryClassName());
        final Constructor<AbstractRouterFactory> constructor = getConstructor(routerFactoryClass);
        final AbstractRouterFactory routerFactory = createInstance(constructor);
        logger.debug("Router factory {} created", routerFactory);
        return routerFactory;
    }

    private AbstractRouterFactory createInstance(final Constructor<AbstractRouterFactory> constructor)
            throws RouterException {
        try {
            return constructor.newInstance(this);
        } catch (final Exception e) {
            throw new RouterException("Could not create a router factory using constructor " + constructor, e);
        }
    }

    private static Constructor<AbstractRouterFactory> getConstructor(final Class<AbstractRouterFactory> clazz)
            throws RouterException {
        try {
            return clazz.getConstructor(PortMapperApp.class);
        } catch (final NoSuchMethodException e) {
            throw new RouterException("Could not find constructor of " + clazz.getName(), e);
        } catch (final SecurityException e1) {
            throw new RouterException("Could not find constructor of " + clazz.getName(), e1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<AbstractRouterFactory> getClassForName(final String className) throws RouterException {
        try {
            return (Class<AbstractRouterFactory>) Class.forName(className);
        } catch (final ClassNotFoundException e) {
            throw new RouterException("Did not find router factory class for name " + className, e);
        }
    }

    public void disconnectRouter() {
        if (this.router == null) {
            logger.debug("Not connected to router. Can not disconnect.");
            return;
        }

        this.router.disconnect();
        this.router = null;
        // View may already be torn down if we're in the shutdown hook; guard.
        if (this.view != null) {
            this.view.fireConnectionStateChange();
        }
    }

    public IRouter getRouter() {
        return router;
    }

    public Settings getSettings() {
        return settings;
    }

    public boolean isConnected() {
        return this.getRouter() != null;
    }

    /**
     * Get the IP address of the local host.
     *
     * @return IP address of the local host or <code>null</code>, if the address could not be determined.
     */
    public String getLocalHostAddress() {

        try {
            if (router != null) {
                logger.debug("Connected to router, get IP of localhost from socket...");
                return router.getLocalHostAddress();
            }

            logger.debug("Not connected to router, get IP of localhost from network interface...");
            final InetAddress address = getLocalhostAddressFromNetworkInterface();
            if (address != null) {
                return address.getHostAddress();
            } else {
                logger.warn("Did not get IP of localhost from network interface");
            }

        } catch (final RouterException e) {
            logger.warn("Could not get address of localhost.", e);
            logger.warn("Could not get address of localhost. Please enter it manually.");
        }
        return null;
    }

    private InetAddress getLocalhostAddressFromNetworkInterface() throws RouterException {
        try {
            final List<NetworkInterface> networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            logger.trace("Found network interfaces {}", networkInterfaces);
            for (final NetworkInterface nInterface : networkInterfaces) {
                if (nInterface.isLoopback()) {
                    logger.debug("Found loopback network interface {}/{} with IPs {}: ignore.",
                            nInterface.getDisplayName(), nInterface.getName(), nInterface.getInterfaceAddresses());
                } else if (!nInterface.isUp()) {
                    logger.debug("Found inactive network interface {}/{} with IPs {}: ignore.",
                            nInterface.getDisplayName(), nInterface.getName(), nInterface.getInterfaceAddresses());
                } else {
                    logger.debug("Found network interface {}/{} with IPs {}: use this one.",
                            nInterface.getDisplayName(), nInterface.getName(), nInterface.getInterfaceAddresses());
                    final List<InetAddress> addresses = Collections.list(nInterface.getInetAddresses());
                    if (!addresses.isEmpty()) {
                        final InetAddress address = findIPv4Adress(nInterface, addresses);
                        logger.debug("Found one address for network interface {}: using {}", nInterface.getName(),
                                address);
                        return address;
                    }
                    logger.debug("Network interface {} has no addresses.", nInterface.getName());
                }
            }
        } catch (final SocketException e) {
            throw new RouterException("Did not get network interfaces.", e);
        }
        return null;
    }

    private InetAddress findIPv4Adress(final NetworkInterface nInterface, final List<InetAddress> addresses) {
        if (addresses.size() == 1) {
            return addresses.get(0);
        }

        for (final InetAddress inetAddress : addresses) {
            if (inetAddress.getHostAddress().contains(".")) {
                logger.debug("Found IPv4 address {}", inetAddress);
                return inetAddress;
            }
        }
        final InetAddress address = addresses.get(0);
        logger.info("Found more than one address for network interface {}: using {}", nInterface.getName(), address);
        return address;
    }

    public void setLogLevel(final String logLevel) {
        this.logbackConfig.setLogLevel(logLevel);
    }
}
