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
package org.chris.portmapper.gui;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.Border;

import org.chris.portmapper.Actions;
import org.chris.portmapper.Messages;
import org.chris.portmapper.PortMapperApp;
import org.chris.portmapper.model.PortMapping;
import org.chris.portmapper.model.PortMappingPreset;
import org.chris.portmapper.router.IRouter;
import org.chris.portmapper.router.RouterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.miginfocom.swing.MigLayout;

/**
 * The main view. Owns the application's primary {@link JFrame} and exposes the
 * JavaBean-style {@code PropertyChangeSupport} surface that {@link Actions#createBound}
 * subscribes to when binding action enabled-state to view properties. Replaces
 * the BSAF {@code FrameView} base class.
 */
public class PortMapperView {

    private static final String MAIN_FRAME_ROUTER_NOT_CONNECTED = "mainFrame.router.not_connected";
    private static final String ACTION_SHOW_ABOUT_DIALOG = "mainFrame.showAboutDialog";
    private static final String ACTION_DISPLAY_ROUTER_INFO = "mainFrame.router.info";
    private static final String ACTION_CONNECT_ROUTER = "mainFrame.router.connect";
    private static final String ACTION_DISCONNECT_ROUTER = "mainFrame.router.disconnect";
    private static final String ACTION_COPY_INTERNAL_ADDRESS = "mainFrame.router.copyInternalAddress";
    private static final String ACTION_COPY_EXTERNAL_ADDRESS = "mainFrame.router.copyExternalAddress";
    private static final String ACTION_UPDATE_ADDRESSES = "mainFrame.router.updateAddresses";
    private static final String ACTION_UPDATE_PORT_MAPPINGS = "mainFrame.mappings.update";

    private static final String ACTION_PORTMAPPER_SETTINGS = "mainFrame.portmapper.settings";

    private static final String ACTION_REMOVE_MAPPINGS = "mainFrame.mappings.remove";

    private static final String ACTION_CREATE_PRESET_MAPPING = "mainFrame.preset_mappings.create";
    private static final String ACTION_EDIT_PRESET_MAPPING = "mainFrame.preset_mappings.edit";
    private static final String ACTION_REMOVE_PRESET_MAPPING = "mainFrame.preset_mappings.remove";
    private static final String ACTION_USE_PRESET_MAPPING = "mainFrame.preset_mappings.use";

    private static final Logger logger = LoggerFactory.getLogger(PortMapperView.class);

    private static final String PROPERTY_MAPPING_SELECTED = "mappingSelected";
    private static final String PROPERTY_ROUTER_CONNECTED = "connectedToRouter";
    private static final String PROPERTY_PRESET_MAPPING_SELECTED = "presetMappingSelected";

    private PortMappingsTableModel tableModel;
    private JTable mappingsTable;
    private JLabel externalIPLabel;
    private JLabel internalIPLabel;
    private JList<PortMappingPreset> portMappingPresets;
    private final PortMapperApp app;
    private final JFrame frame;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    @SuppressWarnings("this-escape")
    public PortMapperView(final PortMapperApp app) {
        this.app = app;
        this.frame = new JFrame(Messages.get("mainFrame.title"));
        this.frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        initView();
        this.frame.pack();
    }

    private void initView() {
        // Create and set up the window.
        final JPanel panel = new JPanel();
        panel.setLayout(new MigLayout("", "[fill, grow]", "[grow 50]unrelated[]unrelated[grow 50]"));

        panel.add(getMappingsPanel(), "wrap");
        panel.add(getRouterPanel(), "grow 0, split 2");
        panel.add(getPresetPanel(), "wrap");
        panel.add(getLogPanel(), "wrap");

        frame.setContentPane(panel);
    }

    /** @return the application's main top-level window. */
    public JFrame getFrame() {
        return frame;
    }

    public void addPropertyChangeListener(final PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(final String propertyName, final PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    protected void firePropertyChange(final String propertyName, final boolean oldValue, final boolean newValue) {
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }

    private JComponent getRouterPanel() {
        final JPanel routerPanel = new JPanel(new MigLayout("", "[fill, grow][]", ""));
        routerPanel
                .setBorder(BorderFactory.createTitledBorder(Messages.get("mainFrame.router.title")));

        routerPanel.add(new JLabel(Messages.get("mainFrame.router.external_address")), "align label"); //$NON-NLS-2$
        externalIPLabel = new JLabel(Messages.get(MAIN_FRAME_ROUTER_NOT_CONNECTED));
        routerPanel.add(externalIPLabel, "width 130!");
        routerPanel.add(new JButton(Actions.createBound(ACTION_COPY_EXTERNAL_ADDRESS,
                e -> copyExternalAddress(), this, PROPERTY_ROUTER_CONNECTED, isConnectedToRouter())),
                "sizegroup router");
        routerPanel.add(new JButton(Actions.createBound(ACTION_UPDATE_ADDRESSES,
                e -> updateAddresses(), this, PROPERTY_ROUTER_CONNECTED, isConnectedToRouter())),
                "wrap, spany 2, aligny base, sizegroup router");

        routerPanel.add(new JLabel(Messages.get("mainFrame.router.internal_address")), "align label");
        internalIPLabel = new JLabel(Messages.get(MAIN_FRAME_ROUTER_NOT_CONNECTED));
        routerPanel.add(internalIPLabel, "width 130!");
        routerPanel.add(new JButton(Actions.createBound(ACTION_COPY_INTERNAL_ADDRESS,
                e -> copyInternalAddress(), this, PROPERTY_ROUTER_CONNECTED, isConnectedToRouter())),
                "wrap, sizegroup router");

        // Keep both connect and disconnect Actions as locals so the
        // property-change listener can swap between them as connection state flips.
        final Action connectAction = Actions.create(ACTION_CONNECT_ROUTER, e -> connectRouter());
        final Action disconnectAction = Actions.create(ACTION_DISCONNECT_ROUTER, e -> disconnectRouter());
        final JButton connectDisconnectButton = new JButton(connectAction);
        routerPanel.add(connectDisconnectButton, "");
        routerPanel.add(new JButton(Actions.createBound(ACTION_DISPLAY_ROUTER_INFO,
                e -> displayRouterInfo(), this, PROPERTY_ROUTER_CONNECTED, isConnectedToRouter())),
                "sizegroup router");
        routerPanel.add(new JButton(Actions.create(ACTION_SHOW_ABOUT_DIALOG, e -> showAboutDialog())),
                "sizegroup router, wrap");

        this.addPropertyChangeListener(evt -> {
            if (PROPERTY_ROUTER_CONNECTED.equals(evt.getPropertyName())) {
                logger.debug("Connection state changed to {}", evt.getNewValue());
                connectDisconnectButton.setAction(
                        Boolean.TRUE.equals(evt.getNewValue()) ? disconnectAction : connectAction);
            }
        });
        routerPanel.add(new JButton(Actions.create(ACTION_PORTMAPPER_SETTINGS, e -> changeSettings())), "");

        return routerPanel;
    }

    private JComponent getLogPanel() {

        final LogTextArea logTextArea = new LogTextArea();

        app.setLogMessageListener(logTextArea);

        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(logTextArea);

        final JPanel logPanel = new JPanel(new MigLayout("", "[grow, fill]", "[grow, fill]"));
        logPanel.setBorder(
                BorderFactory.createTitledBorder(Messages.get("mainFrame.log_messages.title")));
        logPanel.add(scrollPane, "height 100::");

        return logPanel;
    }

    private JComponent getPresetPanel() {
        final JPanel presetPanel = new JPanel(new MigLayout("", "[grow, fill][]", ""));
        presetPanel.setBorder(BorderFactory
                .createTitledBorder(Messages.get("mainFrame.port_mapping_presets.title")));

        portMappingPresets = new JList<>(new PresetListModel(app.getSettings()));
        portMappingPresets.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        portMappingPresets.setLayoutOrientation(JList.VERTICAL);

        portMappingPresets.addListSelectionListener(e -> {
            logger.trace("Selection of preset list has changed: {}", isPresetMappingSelected());
            firePropertyChange(PROPERTY_PRESET_MAPPING_SELECTED, false, isPresetMappingSelected());
        });

        presetPanel.add(new JScrollPane(portMappingPresets), "spany 4, grow");

        presetPanel.add(new JButton(Actions.create(ACTION_CREATE_PRESET_MAPPING,
                e -> createPresetMapping())), "wrap, sizegroup preset_buttons");
        presetPanel.add(new JButton(Actions.createBound(ACTION_EDIT_PRESET_MAPPING,
                e -> editPresetMapping(), this, PROPERTY_PRESET_MAPPING_SELECTED, isPresetMappingSelected())),
                "wrap, sizegroup preset_buttons");
        presetPanel.add(new JButton(Actions.createBound(ACTION_REMOVE_PRESET_MAPPING,
                e -> removePresetMapping(), this, PROPERTY_PRESET_MAPPING_SELECTED, isPresetMappingSelected())),
                "wrap, sizegroup preset_buttons");
        presetPanel.add(new JButton(Actions.createBound(ACTION_USE_PRESET_MAPPING,
                e -> addPresetMapping(), this, PROPERTY_PRESET_MAPPING_SELECTED, isPresetMappingSelected())),
                "wrap, sizegroup preset_buttons");

        return presetPanel;
    }

    private JComponent getMappingsPanel() {
        // Mappings panel

        tableModel = new PortMappingsTableModel();
        mappingsTable = new JTable(tableModel);
        mappingsTable.setAutoCreateRowSorter(true);
        mappingsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        mappingsTable.setSize(new Dimension(400, 100));
        mappingsTable.getSelectionModel().addListSelectionListener(
                e -> firePropertyChange(PROPERTY_MAPPING_SELECTED, false, isMappingSelected()));

        final JScrollPane mappingsTabelPane = new JScrollPane();
        mappingsTabelPane.setViewportView(mappingsTable);

        final JPanel mappingsPanel = new JPanel(new MigLayout("", "[fill,grow]", "[grow,fill][]"));
        mappingsPanel.setName("port_mappings");
        final Border panelBorder = BorderFactory
                .createTitledBorder(Messages.get("mainFrame.port_mappings.title"));
        mappingsPanel.setBorder(panelBorder);
        mappingsPanel.add(mappingsTabelPane, "height 100::, span 2, wrap");

        mappingsPanel.add(new JButton(Actions.createBound(ACTION_REMOVE_MAPPINGS,
                e -> removeMappings(), this, PROPERTY_MAPPING_SELECTED, isMappingSelected())), "");
        mappingsPanel.add(new JButton(Actions.createBound(ACTION_UPDATE_PORT_MAPPINGS,
                e -> updatePortMappings(), this, PROPERTY_ROUTER_CONNECTED, isConnectedToRouter())), "wrap");
        return mappingsPanel;
    }

    /**
     * Refresh the internal and external IP labels. The SOAP/HTTP calls to the
     * router run on a {@link RouterWorker} so the EDT stays responsive; only
     * label updates happen on the EDT.
     */
    public void updateAddresses() {
        final IRouter router = app.getRouter();
        if (router == null) {
            externalIPLabel.setText(Messages.get(MAIN_FRAME_ROUTER_NOT_CONNECTED));
            internalIPLabel.setText(Messages.get(MAIN_FRAME_ROUTER_NOT_CONNECTED));
            return;
        }
        externalIPLabel.setText(Messages.get("mainFrame.router.updating"));
        internalIPLabel.setText(Messages.get("mainFrame.router.updating"));
        new RouterWorker<Addresses>("get router addresses") {
            @Override
            protected Addresses doInBackground() throws RouterException {
                return new Addresses(router.getInternalHostName(), router.getExternalIPAddress());
            }

            @Override
            protected void onSuccess(final Addresses addresses) {
                internalIPLabel.setText(addresses.internal());
                externalIPLabel.setText(addresses.external() != null ? addresses.external() : "");
            }

            @Override
            protected void onFailure(final Throwable cause) {
                super.onFailure(cause);
                externalIPLabel.setText("");
            }
        }.execute();
    }

    /** A pair of router-reported addresses. */
    private record Addresses(String internal, String external) {
    }

    /**
     * Start the async connect-to-router task. Previously used BSAF's
     * {@code Application.getContext().getTaskService().execute(task)}; now
     * just calls {@link SwingWorker#execute()} directly. {@link ConnectTask}
     * extends {@code SwingWorker}; its UI-touching work happens in {@code done()}
     * on the EDT (the BSAF version mistakenly touched Swing labels from
     * {@code doInBackground} which ran on a worker thread).
     */
    public void connectRouter() {
        new ConnectTask(app).execute();
    }

    public void disconnectRouter() {
        app.disconnectRouter();
        updateAddresses();
        updatePortMappings();
    }

    private void addMapping(final Collection<PortMapping> portMappings) {
        final IRouter router = app.getRouter();
        if (router == null) {
            return;
        }

        try {
            router.addPortMappings(portMappings);
            logger.info("{} port mapping added successfully", portMappings.size());
        } catch (final RouterException e) {
            logger.error("Could not add port mapping", e);
            JOptionPane.showMessageDialog(this.getFrame(), "The port mapping could not be added.\n" + e.getMessage(),
                    "Error adding port mapping", JOptionPane.WARNING_MESSAGE);
        }

        this.updatePortMappings();
    }

    /**
     * Remove the currently-selected port mappings. The per-mapping
     * {@code removeMapping} SOAP calls happen off the EDT in a
     * {@link RouterWorker}; the table refresh fires from {@code done()}.
     */
    public void removeMappings() {
        final Collection<PortMapping> selectedMappings = this.getSelectedPortMappings();
        if (selectedMappings.isEmpty()) {
            return;
        }
        final IRouter router = app.getRouter();
        new RouterWorker<Void>("remove " + selectedMappings.size() + " port mapping(s)") {
            @Override
            protected Void doInBackground() throws RouterException {
                for (final PortMapping mapping : selectedMappings) {
                    logger.info("Removing mapping {}", mapping);
                    router.removeMapping(mapping);
                    logger.info("Mapping was removed successfully: {}", mapping);
                }
                return null;
            }

            @Override
            protected void onSuccess(final Void v) {
                updatePortMappings();
            }

            @Override
            protected void onFailure(final Throwable cause) {
                super.onFailure(cause);
                // Refresh the table anyway — some mappings may have been removed before the failure.
                updatePortMappings();
            }
        }.execute();
    }

    /**
     * Log router info to the in-app log panel. The {@code logRouterInfo} call
     * itself blocks on SOAP, so it runs off the EDT in a {@link RouterWorker}.
     */
    public void displayRouterInfo() {
        final IRouter router = app.getRouter();
        if (router == null) {
            logger.warn("Not connected to router, could not get router info");
            return;
        }
        new RouterWorker<Void>("get router info") {
            @Override
            protected Void doInBackground() throws RouterException {
                router.logRouterInfo();
                return null;
            }
        }.execute();
    }

    public void showAboutDialog() {
        app.show(new AboutDialog(app));
    }

    public void copyInternalAddress() {
        this.copyTextToClipboard(this.internalIPLabel.getText());
    }

    public void copyExternalAddress() {
        this.copyTextToClipboard(this.externalIPLabel.getText());
    }

    /**
     * Refresh the port-mappings table. The enumeration loop runs against the
     * router (potentially many SOAP calls) so it goes off the EDT in a
     * {@link RouterWorker}; only the table-model update happens on the EDT.
     */
    public void updatePortMappings() {
        final IRouter router = app.getRouter();
        if (router == null) {
            this.tableModel.setMappings(Collections.<PortMapping> emptyList());
            return;
        }
        new RouterWorker<Collection<PortMapping>>("list port mappings") {
            @Override
            protected Collection<PortMapping> doInBackground() throws RouterException {
                return router.getPortMappings();
            }

            @Override
            protected void onSuccess(final Collection<PortMapping> mappings) {
                logger.info("Found {} mappings", mappings.size());
                tableModel.setMappings(mappings);
            }
        }.execute();
    }

    public void addPresetMapping() {
        final PortMappingPreset selectedItem = this.portMappingPresets.getSelectedValue();
        if (selectedItem != null) {
            final String localHostAddress = app.getLocalHostAddress();
            if (selectedItem.useLocalhostAsInternalClient() && localHostAddress == null) {
                JOptionPane.showMessageDialog(this.getFrame(),
                        Messages.get("messages.error_getting_localhost_address"),
                        Messages.get("messages.error"), JOptionPane.ERROR_MESSAGE);
            } else {
                addMapping(selectedItem.getPortMappings(localHostAddress));
            }
        }
    }

    public void createPresetMapping() {
        app.show(new EditPresetDialog(app));
    }

    public void editPresetMapping() {
        final PortMappingPreset selectedPreset = this.portMappingPresets.getSelectedValue();
        app.show(new EditPresetDialog(app, selectedPreset));
    }

    public void changeSettings() {
        logger.debug("Open Settings dialog");
        app.show(new SettingsDialog(app));
    }

    public void removePresetMapping() {
        final PortMappingPreset selectedPreset = this.portMappingPresets.getSelectedValue();
        app.getSettings().removePresets(selectedPreset);
    }

    public void fireConnectionStateChange() {
        firePropertyChange(PROPERTY_ROUTER_CONNECTED, !isConnectedToRouter(), isConnectedToRouter());
    }

    public boolean isConnectedToRouter() {
        return app.isConnected();
    }

    public boolean isMappingSelected() {
        return this.isConnectedToRouter() && !this.getSelectedPortMappings().isEmpty();
    }

    public boolean isPresetMappingSelected() {
        return this.portMappingPresets.getSelectedValue() != null;
    }

    /**
     * Get the port mappings currently selected in the table.
     *
     * @return the currently selected port mappings.
     */
    public Collection<PortMapping> getSelectedPortMappings() {
        final int[] selectedRows = mappingsTable.getSelectedRows();
        if (selectedRows == null || selectedRows.length == 0) {
            return Collections.emptyList();
        }
        final Collection<PortMapping> selectedMappings = new ArrayList<>(selectedRows.length);
        for (final int rowIndex : selectedRows) {
            if (rowIndex >= 0) {
                // The table could be sorted, so convert the row index for
                // the model
                final int modelRowIndex = mappingsTable.convertRowIndexToModel(rowIndex);
                final PortMapping mapping = tableModel.getPortMapping(modelRowIndex);
                if (mapping != null) {
                    selectedMappings.add(mapping);
                }
            }
        }
        return selectedMappings;
    }

    private void copyTextToClipboard(final String text) {
        final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        logger.trace("Copy text '{}' to clipbord", text);
        clipboard.setContents(new StringSelection(text), (clip, contents) -> logger.trace("Lost clipboard ownership"));
    }

    /**
     * Async router-connect work. {@code doInBackground} runs the blocking
     * discovery off the EDT; {@code done()} (on the EDT) shows the
     * multi-router-selection {@link JOptionPane} when necessary and then
     * assigns the connected router. The pre-modernisation code put the
     * selection prompt inside the worker-thread call to
     * {@code app.connectRouter()}, which violated Swing's EDT contract — the
     * split here is what fixes review finding F3.
     */
    private class ConnectTask extends SwingWorker<Collection<IRouter>, Void> {

        private final PortMapperApp app;

        ConnectTask(final PortMapperApp app) {
            this.app = app;
        }

        @Override
        protected Collection<IRouter> doInBackground() throws Exception {
            logger.trace("Discovering routers...");
            return app.discoverRouters();
        }

        @Override
        protected void done() {
            final Collection<IRouter> found;
            try {
                found = get();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (final ExecutionException e) {
                logger.warn("Could not connect to router: {}", e.getCause().getMessage(), e.getCause());
                return;
            }
            if (found.isEmpty()) {
                return; // Already-connected branch; nothing to do.
            }
            final IRouter selected = found.size() == 1 ? found.iterator().next() : promptForRouter(found);
            if (selected == null) {
                logger.info("No router selected.");
                return;
            }
            app.setRouter(selected);
            logger.trace("Updating addresses...");
            updateAddresses();
            logger.trace("Updating port mappings...");
            updatePortMappings();
            logger.trace("done");
        }

        private IRouter promptForRouter(final Collection<IRouter> candidates) {
            logger.info("Found more than one router (count: {}): ask user.", candidates.size());
            return (IRouter) JOptionPane.showInputDialog(getFrame(),
                    Messages.get("messages.select_router.message"),
                    Messages.get("messages.select_router.title"), JOptionPane.QUESTION_MESSAGE, null,
                    candidates.toArray(), null);
        }
    }

    /**
     * Base class for any router-call wrapped in a {@link SwingWorker}: the
     * blocking work lives in {@link #doInBackground()} (off-EDT); the success
     * and failure handlers ({@link #onSuccess}, {@link #onFailure}) run on the
     * EDT inside {@link #done()}. Centralises the exception unwrapping so each
     * caller doesn't repeat the {@code InterruptedException}/{@code ExecutionException}
     * boilerplate.
     *
     * @param <T> the type of result produced by the background work.
     */
    private abstract class RouterWorker<T> extends SwingWorker<T, Void> {
        private final String operationDescription;

        RouterWorker(final String operationDescription) {
            this.operationDescription = operationDescription;
        }

        /** Off-EDT blocking work. Override and return the result. */
        @Override
        protected abstract T doInBackground() throws RouterException;

        /** EDT-only success handler. Default is a no-op. */
        protected void onSuccess(final T result) {
            // default: nothing
        }

        /** EDT-only failure handler. Default logs at WARN with the operation description. */
        protected void onFailure(final Throwable cause) {
            logger.warn("Router operation failed ({}): {}", operationDescription, cause.getMessage(), cause);
        }

        @Override
        protected final void done() {
            try {
                onSuccess(get());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final ExecutionException e) {
                onFailure(e.getCause());
            }
        }
    }
}
