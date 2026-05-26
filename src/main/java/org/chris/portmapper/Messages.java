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

import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Localized message lookup for the application. Replaces BSAF's
 * {@code org.jdesktop.application.ResourceMap} with a plain {@link ResourceBundle}
 * plus recursive {@code ${otherKey}} placeholder substitution (which JDK's
 * {@code ResourceBundle.getString} does not do).
 *
 * <p>The bundle is loaded once at class init from
 * {@code org.chris.portmapper.resources.PortMapperApp}, in the JVM's default
 * locale. Build-time resource processing renames the source {@code _en.properties}
 * file to the base {@code .properties} (no-suffix default) and {@code _zh_CN.properties}
 * to {@code _zh.properties}; placeholders like {@code @VERSION_NUMBER@} are
 * substituted at the same time. See {@code build.gradle} {@code processResources}.
 */
public final class Messages {

    private static final String BUNDLE_NAME = "org.chris.portmapper.resources.PortMapperApp";
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

    private Messages() {
        // not instantiable
    }

    /**
     * Look up a localized message by key, recursively substituting any
     * {@code ${otherKey}} references in the value with their resolved strings.
     *
     * @param key the bundle key.
     * @return the resolved message.
     * @throws java.util.MissingResourceException if the key isn't in the bundle.
     * @throws IllegalStateException if a circular placeholder reference is detected.
     */
    public static String get(final String key) {
        return resolve(key, BUNDLE, new HashSet<>());
    }

    /**
     * Package-private resolver that takes an explicit bundle, so tests can drive the
     * placeholder logic against synthetic bundles without touching the live one.
     *
     * @param key the bundle key.
     * @param bundle the bundle to resolve against.
     * @param visiting recursion guard tracking keys currently being resolved.
     * @return the resolved message.
     */
    static String resolve(final String key, final ResourceBundle bundle, final Set<String> visiting) {
        if (!visiting.add(key)) {
            throw new IllegalStateException("Circular placeholder reference involving key '" + key + "'");
        }
        try {
            final String template = bundle.getString(key);
            final Matcher m = PLACEHOLDER.matcher(template);
            final StringBuilder result = new StringBuilder();
            while (m.find()) {
                final String inner = m.group(1);
                m.appendReplacement(result, Matcher.quoteReplacement(resolve(inner, bundle, visiting)));
            }
            m.appendTail(result);
            return result.toString();
        } finally {
            visiting.remove(key);
        }
    }
}
