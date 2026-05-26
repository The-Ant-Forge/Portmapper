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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Messages} — bundle lookup against the live app bundle,
 * plus placeholder-substitution edge cases against synthetic bundles
 * (so cycle detection and chained references can be exercised in isolation).
 */
class TestMessages {

    @Test
    void getReturnsLiteralWhenNoPlaceholder() {
        assertEquals("Router", Messages.get("mainFrame.router.title"));
    }

    @Test
    void getSubstitutesTopLevelPlaceholders() {
        // mainFrame.title=${Application.title} ${Application.version}
        // Application.title=PortMapper, Application.version=<build-substituted>
        final String result = Messages.get("mainFrame.title");
        assertEquals(true, result.startsWith("PortMapper "), () -> "Unexpected: " + result);
        assertFalse(result.contains("${"), () -> "Unresolved placeholder: " + result);
    }

    @Test
    void getResolvesNestedReferencesFromAboutDialog() {
        // about_dialog.upnplib_label.toolTipText contains three ${...} refs
        final String result = Messages.get("about_dialog.upnplib_label.toolTipText");
        assertFalse(result.contains("${"), () -> "Unresolved placeholder: " + result);
    }

    @Test
    void unknownKeyThrowsMissingResource() {
        assertThrows(MissingResourceException.class, () -> Messages.get("this.key.does.not.exist"));
    }

    @Test
    void synthetic_circularReferenceIsDetected() {
        final ResourceBundle bundle = bundleFromMap(Map.of(
                "a", "${b}",
                "b", "${a}"));
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Messages.resolve("a", bundle, new HashSet<>()));
        assertEquals(true, ex.getMessage().contains("Circular"));
    }

    @Test
    void synthetic_chainedPlaceholdersResolveInOrder() {
        final ResourceBundle bundle = bundleFromMap(Map.of(
                "greeting", "Hello, ${name}!",
                "name", "${first} ${last}",
                "first", "Ada",
                "last", "Lovelace"));
        assertEquals("Hello, Ada Lovelace!", Messages.resolve("greeting", bundle, new HashSet<>()));
    }

    @Test
    void synthetic_multiplePlaceholdersInOneValue() {
        final ResourceBundle bundle = bundleFromMap(Map.of(
                "msg", "${a} and ${b}",
                "a", "A",
                "b", "B"));
        assertEquals("A and B", Messages.resolve("msg", bundle, new HashSet<>()));
    }

    @Test
    void synthetic_dollarAndBackslashInReplacementAreLiteral() {
        // Matcher.appendReplacement treats $ and \ specially in the replacement; the
        // implementation must quote them or the resolved value becomes garbage.
        final ResourceBundle bundle = bundleFromMap(Map.of(
                "msg", "Price: ${price}",
                "price", "$5.00 \\ cash"));
        assertEquals("Price: $5.00 \\ cash", Messages.resolve("msg", bundle, new HashSet<>()));
    }

    private static ResourceBundle bundleFromMap(final Map<String, String> entries) {
        return new ResourceBundle() {
            @Override
            protected Object handleGetObject(final String key) {
                return entries.get(key);
            }

            @Override
            public java.util.Enumeration<String> getKeys() {
                return java.util.Collections.enumeration(entries.keySet());
            }
        };
    }
}
