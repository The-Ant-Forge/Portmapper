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

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * File-based persistence for {@link Settings} (and any other JavaBean) using
 * the JDK's {@link XMLEncoder}/{@link XMLDecoder}. Replaces BSAF's
 * {@code org.jdesktop.application.LocalStorage} - BSAF used XMLEncoder under
 * the hood too, so the on-disk format is the standard {@code java.beans} XML
 * schema. Existing BSAF-written {@code settings.xml} files may or may not
 * round-trip cleanly depending on whether BSAF registered any custom
 * {@code PersistenceDelegate} for the loaded type; the load path falls back
 * gracefully to a fresh model on any failure.
 *
 * <p>The storage directory is selected with the same precedence as the
 * legacy BSAF configuration so the on-disk path doesn't change for existing
 * users.
 */
public final class SettingsStorage {

    private static final String CONFIG_DIR_PROPERTY = "portmapper.config.dir";
    private static final String PORTABLE_CONFIG_DIR_NAME = "PortMapperConf";
    private static final String VENDOR_ID = "UnknownApplicationVendor";
    private static final String APP_ID = "PortMapper";

    private final File directory;

    /** Package-private for tests. */
    SettingsStorage(final File directory) {
        this.directory = directory;
    }

    /**
     * Pick the storage directory using the same precedence as the legacy
     * BSAF-backed configuration:
     * <ol>
     *   <li>System property {@code portmapper.config.dir} if set (fatal if
     *       it doesn't point to a usable directory).</li>
     *   <li>{@code ./PortMapperConf} relative to the current working
     *       directory if it exists and is readable/writable (portable app).</li>
     *   <li>Per-OS user app-data location: on Windows
     *       {@code %APPDATA%\UnknownApplicationVendor\PortMapper}; on macOS
     *       {@code ~/Library/Application Support/PortMapper}; otherwise
     *       {@code ~/.PortMapper}.</li>
     * </ol>
     *
     * @return a configured {@code SettingsStorage}.
     * @throws IllegalStateException if {@code portmapper.config.dir} is set
     *         but unusable.
     */
    public static SettingsStorage discover() {
        final String custom = System.getProperty(CONFIG_DIR_PROPERTY);
        if (custom != null) {
            final File dir = new File(custom);
            if (!dir.isDirectory() || !dir.canRead() || !dir.canWrite()) {
                throw new IllegalStateException(
                        "Custom configuration directory '" + custom + "' is not a usable directory");
            }
            return new SettingsStorage(dir);
        }
        final File portable = new File(PORTABLE_CONFIG_DIR_NAME);
        if (portable.isDirectory() && portable.canRead() && portable.canWrite()) {
            return new SettingsStorage(portable);
        }
        return new SettingsStorage(defaultUserDirectory());
    }

    /**
     * Return the per-OS default storage directory, matching BSAF's
     * {@code LocalStorage} default-path logic so existing settings files keep
     * the same on-disk location.
     *
     * @return the default storage directory for the current platform.
     */
    static File defaultUserDirectory() {
        final String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.startsWith("windows")) {
            final String appData = System.getenv("APPDATA");
            final String home = System.getProperty("user.home");
            final File base = appData != null ? new File(appData) : new File(home, "AppData/Roaming");
            return new File(base, VENDOR_ID + File.separator + APP_ID);
        }
        if (os.startsWith("mac")) {
            return new File(System.getProperty("user.home"), "Library/Application Support/" + APP_ID);
        }
        return new File(System.getProperty("user.home"), "." + APP_ID);
    }

    /** @return the storage directory in use. */
    public File getDirectory() {
        return directory;
    }

    /**
     * Load an object previously written by {@link #save}.
     *
     * @param filename the file under the storage directory.
     * @param type the expected concrete type for a checked downcast.
     * @param <T> the expected type.
     * @return the deserialized object.
     * @throws IOException if the file cannot be read or the contents are not
     *         a valid {@code java.beans} XML document or do not cast to
     *         {@code type}.
     */
    public <T> T load(final String filename, final Class<T> type) throws IOException {
        final File file = new File(directory, filename);
        try (FileInputStream in = new FileInputStream(file);
             BufferedInputStream bin = new BufferedInputStream(in);
             XMLDecoder decoder = new XMLDecoder(bin)) {
            final Object obj = decoder.readObject();
            if (!type.isInstance(obj)) {
                throw new IOException("Expected " + type.getName() + " but got "
                        + (obj == null ? "null" : obj.getClass().getName()));
            }
            return type.cast(obj);
        } catch (final RuntimeException e) {
            // XMLDecoder wraps parse failures in unchecked exceptions; normalize to IOException so callers
            // can use a single catch.
            throw new IOException("Failed to decode " + file.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Serialize a JavaBean-compliant object to {@code filename} in the
     * storage directory. Creates the directory if it does not yet exist.
     *
     * @param filename the file under the storage directory.
     * @param object the object to write.
     * @throws IOException if the directory cannot be created or the file
     *         cannot be written.
     */
    public void save(final String filename, final Object object) throws IOException {
        if (!directory.exists()) {
            Files.createDirectories(directory.toPath());
        }
        final File file = new File(directory, filename);
        try (FileOutputStream out = new FileOutputStream(file);
             BufferedOutputStream bout = new BufferedOutputStream(out);
             XMLEncoder encoder = new XMLEncoder(bout)) {
            encoder.writeObject(object);
        }
    }
}
