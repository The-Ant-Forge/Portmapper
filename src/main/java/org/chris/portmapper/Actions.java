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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.MissingResourceException;

import javax.swing.AbstractAction;
import javax.swing.Action;

/**
 * Factory for Swing {@link Action} instances whose display metadata (text,
 * tooltip, mnemonic) is loaded from the application bundle using the same
 * {@code <actionName>.Action.*} key convention that BSAF's
 * {@code @org.jdesktop.application.Action} used. Replaces BSAF's annotation-
 * driven action discovery with explicit factory calls at the layout site.
 *
 * <p>For actions whose enabled state should track a bean property
 * (BSAF's {@code @Action(enabledProperty="...")}), use
 * {@link #createBound(String, ActionListener, Object, String, boolean)}.
 *
 * <p>Mnemonic convention: a single {@code &} in the bundle text marks the
 * next character as the keyboard shortcut (Windows convention). The
 * {@code &} is stripped from the display text and the following character
 * is uppercased and set as {@link Action#MNEMONIC_KEY}. The project's
 * bundle does not use the {@code &&} (literal ampersand) escape; if that
 * ever appears, this parser would mishandle it.
 */
public final class Actions {

    private Actions() {
        // not instantiable
    }

    /**
     * Build an {@link Action} backed by {@code handler}. The action's
     * {@link Action#NAME} is read from {@code actionName + ".Action.text"}
     * (mandatory; {@link MissingResourceException} otherwise). The
     * {@link Action#SHORT_DESCRIPTION} is read from
     * {@code actionName + ".Action.shortDescription"} if present.
     * A leading {@code &X} in the name text sets {@link Action#MNEMONIC_KEY}
     * to the uppercased character code of {@code X}.
     *
     * @param actionName the bundle prefix for this action.
     * @param handler the listener invoked when the action fires.
     * @return a configured {@code Action}.
     */
    public static Action create(final String actionName, final ActionListener handler) {
        String text = Messages.get(actionName + ".Action.text");
        int mnemonic = 0;
        final int ampIndex = text.indexOf('&');
        if (ampIndex >= 0 && ampIndex < text.length() - 1) {
            mnemonic = Character.toUpperCase(text.charAt(ampIndex + 1));
            text = text.substring(0, ampIndex) + text.substring(ampIndex + 1);
        }

        final Action action = new BundleAction(text, handler);
        final String tooltip = optionalString(actionName + ".Action.shortDescription");
        if (tooltip != null) {
            action.putValue(Action.SHORT_DESCRIPTION, tooltip);
        }
        if (mnemonic > 0) {
            action.putValue(Action.MNEMONIC_KEY, mnemonic);
        }
        return action;
    }

    /**
     * Build an action and bind its {@link Action#setEnabled enabled state} to
     * a JavaBean property on {@code bean}, mirroring BSAF's
     * {@code @Action(enabledProperty="propertyName")} behaviour. The bean
     * must expose
     * {@code addPropertyChangeListener(String, PropertyChangeListener)}.
     *
     * @param actionName the bundle prefix for this action.
     * @param handler the listener invoked when the action fires.
     * @param bean the JavaBean owning the property.
     * @param propertyName the property whose changes drive enabled state.
     * @param initiallyEnabled the action's starting enabled state.
     * @return a configured {@code Action}.
     * @throws IllegalStateException if {@code bean} does not expose the
     *         expected {@code addPropertyChangeListener} signature.
     */
    public static Action createBound(final String actionName, final ActionListener handler,
            final Object bean, final String propertyName, final boolean initiallyEnabled) {
        final Action action = create(actionName, handler);
        action.setEnabled(initiallyEnabled);
        subscribePropertyListener(bean, propertyName,
                evt -> action.setEnabled((Boolean) evt.getNewValue()));
        return action;
    }

    private static void subscribePropertyListener(final Object bean, final String propertyName,
            final PropertyChangeListener listener) {
        try {
            final Method m = bean.getClass().getMethod("addPropertyChangeListener",
                    String.class, PropertyChangeListener.class);
            m.invoke(bean, propertyName, listener);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot bind to property '" + propertyName
                    + "' on " + bean.getClass().getName()
                    + ": addPropertyChangeListener(String, PropertyChangeListener) is missing", e);
        }
    }

    private static String optionalString(final String key) {
        try {
            return Messages.get(key);
        } catch (final MissingResourceException e) {
            return null;
        }
    }

    /**
     * Minimal {@link AbstractAction} subclass that delegates to an
     * {@link ActionListener}. Kept private so the only path to an instance
     * is via {@link Actions#create} / {@link Actions#createBound}.
     */
    private static final class BundleAction extends AbstractAction {
        private static final long serialVersionUID = 1L;
        private final transient ActionListener delegate;

        BundleAction(final String name, final ActionListener delegate) {
            super(name);
            this.delegate = delegate;
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
