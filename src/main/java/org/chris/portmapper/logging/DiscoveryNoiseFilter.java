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
package org.chris.portmapper.logging;

import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Logback {@link TurboFilter} that suppresses jUPnP's chatty discovery
 * messages about non-router UPnP devices on the LAN. On a typical home
 * network the discovery phase finds NAS units, Sonos players, Chromecast
 * devices, etc. — jUPnP logs every one of them at INFO/WARN before
 * settling on the actual IGD, swamping the in-app log panel with content
 * the user doesn't care about.
 *
 * <p>Whether the filter actually denies messages is controlled by the
 * {@link BooleanSupplier} passed at construction (so the user can toggle
 * the filter in {@code SettingsDialog} without re-registering it). When
 * the supplier returns {@code false} the filter is inert and returns
 * {@link FilterReply#NEUTRAL} for every event.
 */
public class DiscoveryNoiseFilter extends TurboFilter {

    /**
     * Substring patterns of jUPnP log messages classified as discovery noise.
     * Each is matched as a literal substring against the formatted message
     * template (the un-substituted form, e.g. {@code "Found service of wrong type {}, expected {}."}),
     * which is robust against the device names and URLs that get interpolated
     * at runtime.
     */
    private static final Pattern NOISE = Pattern.compile(
            "Found service of wrong type"
                    + "|Received empty service descriptor"
                    + "|Device service description failed"
                    + "|Service descriptor retrieval failed"
                    + "|UPnP specification violation");

    private final BooleanSupplier enabled;

    public DiscoveryNoiseFilter(final BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @Override
    public FilterReply decide(final Marker marker, final Logger logger, final Level level, final String format,
            final Object[] params, final Throwable t) {
        if (!enabled.getAsBoolean()) {
            return FilterReply.NEUTRAL;
        }
        if (format == null || !logger.getName().startsWith("org.jupnp")) {
            return FilterReply.NEUTRAL;
        }
        if (NOISE.matcher(format).find()) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
