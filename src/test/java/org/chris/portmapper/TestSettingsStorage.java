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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SettingsStorage} - XMLEncoder/XMLDecoder roundtrip and
 * directory-resolution edge cases. The discovery logic (system property,
 * portable dir, OS-specific default) is exercised manually rather than in
 * tests because changing the JVM's system properties and CWD inside a unit
 * test is more fragile than the simple branches it would cover.
 */
class TestSettingsStorage {

    @Test
    void roundtripPreservesSettingsFields(@TempDir final Path dir) throws IOException {
        final SettingsStorage storage = new SettingsStorage(dir.toFile());
        final Settings original = new Settings();
        original.setLogLevel("DEBUG");
        original.setUseEntityEncoding(false);
        original.setRouterFactoryClassName("org.example.SomeFactory");

        storage.save("settings.xml", original);
        final Settings loaded = storage.load("settings.xml", Settings.class);

        assertEquals("DEBUG", loaded.getLogLevel());
        assertEquals(false, loaded.isUseEntityEncoding());
        assertEquals("org.example.SomeFactory", loaded.getRouterFactoryClassName());
    }

    @Test
    void saveCreatesDirectoryIfMissing(@TempDir final Path parent) throws IOException {
        final File nested = parent.resolve("a/b/c").toFile();
        assertTrue(!nested.exists(), "precondition: nested dir does not exist yet");
        final SettingsStorage storage = new SettingsStorage(nested);
        storage.save("settings.xml", new Settings());
        assertTrue(nested.isDirectory());
        assertTrue(new File(nested, "settings.xml").isFile());
    }

    @Test
    void loadMissingFileThrowsIOException(@TempDir final Path dir) {
        final SettingsStorage storage = new SettingsStorage(dir.toFile());
        assertThrows(IOException.class, () -> storage.load("nonexistent.xml", Settings.class));
    }

    @Test
    void loadOfBogusContentThrowsIOException(@TempDir final Path dir) throws IOException {
        final SettingsStorage storage = new SettingsStorage(dir.toFile());
        Files.writeString(dir.resolve("garbage.xml"), "this is not XML");
        assertThrows(IOException.class, () -> storage.load("garbage.xml", Settings.class));
    }

    @Test
    void loadOfWrongTypeThrowsIOException(@TempDir final Path dir) throws IOException {
        final SettingsStorage storage = new SettingsStorage(dir.toFile());
        // Save a String, attempt to load it as Settings - the type check should reject.
        storage.save("string.xml", "just a string");
        assertThrows(IOException.class, () -> storage.load("string.xml", Settings.class));
    }

    @Test
    void defaultUserDirectoryIsAbsolute() {
        // The OS-specific path branches each anchor to user.home or an env var; we don't pin a specific
        // platform here (tests run on whatever OS the dev/CI box is), but the result must be an
        // absolute path so the SETTINGS_FILENAME resolves unambiguously.
        final File dir = SettingsStorage.defaultUserDirectory();
        assertNotNull(dir);
        assertTrue(dir.isAbsolute(), () -> "Expected absolute path, got: " + dir);
    }
}
