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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.MissingResourceException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Action;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Actions} factory: bundle-driven metadata,
 * mnemonic parsing, optional shortDescription, handler invocation,
 * and {@link Actions#createBound} reflective property-listener wiring.
 */
class TestActions {

    private static final String ACTION_PLAIN = "about_dialog.close";          // text="Close", no mnemonic
    private static final String ACTION_WITH_MNEMONIC = "mainFrame.showAboutDialog"; // text="&About...", mnemonic 'A'

    @Test
    void createReadsTextFromBundle() {
        final Action action = Actions.create(ACTION_PLAIN, e -> { /* no-op */ });
        assertEquals("Close", action.getValue(Action.NAME));
    }

    @Test
    void createReadsShortDescriptionFromBundleWhenPresent() {
        final Action action = Actions.create(ACTION_PLAIN, e -> { /* no-op */ });
        assertEquals("Close about dialog", action.getValue(Action.SHORT_DESCRIPTION));
    }

    @Test
    void createStripsAmpersandAndSetsMnemonic() {
        final Action action = Actions.create(ACTION_WITH_MNEMONIC, e -> { /* no-op */ });
        assertEquals("About...", action.getValue(Action.NAME));
        assertEquals(Integer.valueOf(KeyEvent.VK_A), action.getValue(Action.MNEMONIC_KEY));
    }

    @Test
    void createWithoutMnemonicLeavesMnemonicKeyUnset() {
        final Action action = Actions.create(ACTION_PLAIN, e -> { /* no-op */ });
        assertNull(action.getValue(Action.MNEMONIC_KEY));
    }

    @Test
    void createMissingTextKeyThrows() {
        assertThrows(MissingResourceException.class,
                () -> Actions.create("nonexistent.action.name", e -> { /* no-op */ }));
    }

    @Test
    void createdActionInvokesHandlerOnActionPerformed() {
        final AtomicInteger calls = new AtomicInteger();
        final Action action = Actions.create(ACTION_PLAIN, e -> calls.incrementAndGet());
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "test"));
        assertEquals(1, calls.get());
    }

    @Test
    void createBoundSetsInitialEnabledState() {
        final FakeBean bean = new FakeBean();
        final Action action = Actions.createBound(ACTION_PLAIN, e -> { /* no-op */ }, bean, "ready", false);
        assertFalse(action.isEnabled());
    }

    @Test
    void createBoundUpdatesEnabledOnPropertyChange() {
        final FakeBean bean = new FakeBean();
        final Action action = Actions.createBound(ACTION_PLAIN, e -> { /* no-op */ }, bean, "ready", false);

        bean.setReady(true);
        assertTrue(action.isEnabled());

        bean.setReady(false);
        assertFalse(action.isEnabled());
    }

    @Test
    void createBoundOnUnsuitableBeanThrows() {
        // Object has no addPropertyChangeListener(String, PropertyChangeListener) method
        assertThrows(IllegalStateException.class,
                () -> Actions.createBound(ACTION_PLAIN, e -> { /* no-op */ }, new Object(), "x", false));
    }

    @Test
    void boundActionStillCarriesBundleMetadata() {
        // createBound delegates to create, so all the metadata behaviour transfers
        final FakeBean bean = new FakeBean();
        final Action action = Actions.createBound(ACTION_WITH_MNEMONIC, e -> { /* no-op */ }, bean, "ready", true);
        assertEquals("About...", action.getValue(Action.NAME));
        assertEquals(Integer.valueOf(KeyEvent.VK_A), action.getValue(Action.MNEMONIC_KEY));
        assertNotNull(action.getValue(Action.SHORT_DESCRIPTION));
    }

    /**
     * Minimal JavaBean fixture exposing a single boolean property named "ready"
     * with PropertyChangeSupport. Mirrors the bean-property shape that BSAF's
     * AbstractBean (the base class of FrameView) provides to PortMapperView.
     */
    static final class FakeBean {
        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private boolean ready;

        public boolean isReady() {
            return ready;
        }

        public void setReady(final boolean ready) {
            final boolean old = this.ready;
            this.ready = ready;
            pcs.firePropertyChange("ready", old, ready);
        }

        public void addPropertyChangeListener(final String propertyName, final PropertyChangeListener listener) {
            pcs.addPropertyChangeListener(propertyName, listener);
        }
    }
}
