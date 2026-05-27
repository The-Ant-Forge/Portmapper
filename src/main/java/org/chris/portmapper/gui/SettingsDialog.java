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

import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

import org.chris.portmapper.Actions;
import org.chris.portmapper.Messages;
import org.chris.portmapper.PortMapperApp;
import org.chris.portmapper.Settings;
import org.chris.portmapper.router.dummy.DummyRouterFactory;
import org.chris.portmapper.router.jupnp.JUPnPRouterFactory;
import org.chris.portmapper.router.weupnp.WeUPnPRouterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import net.miginfocom.swing.MigLayout;

/**
 * This class represents the settings dialog.
 */
// Deep inheritance hierarchy required by Swing API
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class SettingsDialog extends JDialog {

    private static final List<String> AVAILABLE_ROUTER_FACTORIES = Arrays.asList(JUPnPRouterFactory.class.getName(),
            WeUPnPRouterFactory.class.getName(), DummyRouterFactory.class.getName());

    private static final List<Level> AVAILABLE_LOG_LEVELS = Arrays.asList(Level.TRACE, Level.DEBUG,
            Level.INFO, Level.WARN, Level.ERROR, Level.OFF);

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(SettingsDialog.class);

    private static final String DIALOG_NAME = "settings_dialog";
    private static final String ACTION_SAVE = DIALOG_NAME + ".save";
    private static final String ACTION_CANCEL = DIALOG_NAME + ".cancel";

    private JCheckBox useEntityEncoding;
    private JCheckBox filterDiscoveryNoise;

    private JComboBox<Level> logLevelComboBox;
    private JComboBox<String> routerFactoryClassComboBox;

    private JButton okButton;

    private final transient PortMapperApp app;

    @SuppressWarnings("this-escape")
    public SettingsDialog(final PortMapperApp app) {
        super(app.getMainFrame(), true);
        this.app = app;

        logger.debug("Create settings dialog");
        this.setContentPane(this.getDialogPane());

        this.getRootPane().setDefaultButton(okButton);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.setName(DIALOG_NAME);
        this.setTitle(Messages.get(DIALOG_NAME + ".title"));
        this.setModal(true);
        this.pack();

        // Open ~50px inside the main frame's top-left so the dialog appears
        // visibly anchored to the app, not centred / off in the screen corner.
        // Swing's default JDialog placement is the screen top-left which is
        // surprising when the app window is anywhere else.
        final JFrame mainFrame = app.getMainFrame();
        if (mainFrame != null) {
            this.setLocation(mainFrame.getX() + 50, mainFrame.getY() + 50);
        }

        // Register an action listener that closes the window when the ESC
        // button is pressed
        final KeyStroke escKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true);
        final ActionListener windowCloseActionListener = e -> cancel();
        getRootPane().registerKeyboardAction(windowCloseActionListener, escKeyStroke,
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private static JLabel createLabel(final String name) {
        final JLabel newLabel = new JLabel(Messages.get(name + ".text"));
        newLabel.setName(name);
        return newLabel;
    }

    private JPanel getDialogPane() {
        final Settings settings = app.getSettings();

        final JPanel dialogPane = new JPanel(new MigLayout("", // Layout
                // Constraints
                "[right]rel[left,grow 100]", // Column Constraints
                "")); // Row Constraints

        logger.debug("Use entity encoding is {}", settings.isUseEntityEncoding());
        useEntityEncoding = new JCheckBox(Messages.get("settings_dialog.use_entity_encoding.text"),
                settings.isUseEntityEncoding());
        useEntityEncoding.setName("settings_dialog.use_entity_encoding");

        dialogPane.add(useEntityEncoding, "span 2, wrap");

        filterDiscoveryNoise = new JCheckBox(Messages.get("settings_dialog.filter_discovery_noise.text"),
                settings.isFilterDiscoveryNoise());
        filterDiscoveryNoise.setName("settings_dialog.filter_discovery_noise");

        dialogPane.add(filterDiscoveryNoise, "span 2, wrap");

        dialogPane.add(createLabel("settings_dialog.log_level"));

        logLevelComboBox = new JComboBox<>(new Vector<>(AVAILABLE_LOG_LEVELS));
        logLevelComboBox.setSelectedItem(Level.toLevel(settings.getLogLevel()));

        dialogPane.add(logLevelComboBox, "wrap");

        dialogPane.add(createLabel("settings_dialog.upnp_lib"));

        routerFactoryClassComboBox = new JComboBox<>(new Vector<>(AVAILABLE_ROUTER_FACTORIES));
        routerFactoryClassComboBox.setSelectedItem(settings.getRouterFactoryClassName());
        dialogPane.add(routerFactoryClassComboBox, "span 2, wrap");

        dialogPane.add(new JButton(Actions.create(ACTION_CANCEL, e -> cancel())), "tag cancel, span 2");
        okButton = new JButton(Actions.create(ACTION_SAVE, e -> save()));
        dialogPane.add(okButton, "tag ok, wrap");

        return dialogPane;
    }

    /**
     * This method is executed when the user clicks the save button. The method saves the entered preset
     */
    public void save() {
        final Settings settings = app.getSettings();
        settings.setUseEntityEncoding(useEntityEncoding.isSelected());
        settings.setFilterDiscoveryNoise(filterDiscoveryNoise.isSelected());
        settings.setLogLevel(((Level) logLevelComboBox.getSelectedItem()).toString());
        settings.setRouterFactoryClassName(routerFactoryClassComboBox.getSelectedItem().toString());

        app.setLogLevel(settings.getLogLevel());

        logger.debug("Saved settings {}", settings);
        this.dispose();
    }

    public void cancel() {
        this.dispose();
    }
}
