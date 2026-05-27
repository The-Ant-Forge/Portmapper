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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.chris.portmapper.model.PortMappingPreset;
import org.chris.portmapper.router.jupnp.JUPnPRouterFactory;

import ch.qos.logback.classic.Level;

public class Settings implements Serializable {

    private static final long serialVersionUID = -1349121864190290050L;

    public static final String PROPERTY_PORT_MAPPING_PRESETS = "presets";

    @SuppressWarnings("serial")
    private List<PortMappingPreset> presets;
    private boolean useEntityEncoding;
    private String logLevel;
    private String routerFactoryClassName;
    private boolean filterDiscoveryNoise;

    private transient PropertyChangeSupport propertyChangeSupport;

    @SuppressWarnings("this-escape")
    public Settings() {
        useEntityEncoding = true;
        logLevel = Level.INFO.toString();
        presets = new ArrayList<>();
        routerFactoryClassName = JUPnPRouterFactory.class.getName();
        filterDiscoveryNoise = true;
        propertyChangeSupport = new PropertyChangeSupport(this);
    }

    public void addPropertyChangeListener(final String property, final PropertyChangeListener listener) {
        this.propertyChangeSupport.addPropertyChangeListener(property, listener);
    }

    public List<PortMappingPreset> getPresets() {
        return presets;
    }

    public void setPresets(final List<PortMappingPreset> presets) {
        this.presets = presets;
    }

    public void addPreset(final PortMappingPreset newPreset) {
        final List<PortMappingPreset> oldPresets = new ArrayList<>(this.presets);
        this.presets.add(newPreset);
        this.propertyChangeSupport.firePropertyChange(PROPERTY_PORT_MAPPING_PRESETS, oldPresets,
                new ArrayList<>(this.presets));
    }

    public void removePresets(final PortMappingPreset selectedPreset) {
        final List<PortMappingPreset> oldPresets = new ArrayList<>(this.presets);
        this.presets.remove(selectedPreset);
        this.propertyChangeSupport.firePropertyChange(PROPERTY_PORT_MAPPING_PRESETS, oldPresets,
                new ArrayList<>(this.presets));
    }

    /**
     * Replace an existing preset (matched by object identity / equals) with
     * a new value. Used by {@code EditPresetDialog} to commit edits — since
     * {@link PortMappingPreset} is now a record (immutable), "saving" an
     * edited preset means swapping the old instance for a freshly-built one.
     *
     * @param oldPreset the preset currently in the list to be replaced.
     * @param newPreset the replacement preset.
     */
    public void replacePreset(final PortMappingPreset oldPreset, final PortMappingPreset newPreset) {
        final int index = this.presets.indexOf(oldPreset);
        if (index < 0) {
            return;
        }
        final List<PortMappingPreset> oldPresets = new ArrayList<>(this.presets);
        this.presets.set(index, newPreset);
        this.propertyChangeSupport.firePropertyChange(PROPERTY_PORT_MAPPING_PRESETS, oldPresets,
                new ArrayList<>(this.presets));
    }

    @Override
    public String toString() {
        return "[Settings: presets=" + presets + ", useEntityEncoding=" + useEntityEncoding + ", logLevel=" + logLevel
                + ", routerFactoryClassName=" + routerFactoryClassName
                + ", filterDiscoveryNoise=" + filterDiscoveryNoise + "]";
    }

    public boolean isUseEntityEncoding() {
        return useEntityEncoding;
    }

    public void setUseEntityEncoding(final boolean useEntityEncoding) {
        this.useEntityEncoding = useEntityEncoding;
    }

    public String getLogLevel() {
        return this.logLevel;
    }

    public void setLogLevel(final String logLevel) {
        this.logLevel = logLevel;
    }

    public String getRouterFactoryClassName() {
        // Migration shim: rewrite obsolete factory FQCNs on first read so the
        // next save self-heals the file. Covers:
        //   - org.chris.portmapper.router.cling.ClingRouterFactory (pre-jUPnP)
        //   - org.chris.portmapper.router.sbbi.SBBIRouterFactory (SBBI dropped)
        if ("org.chris.portmapper.router.cling.ClingRouterFactory".equals(routerFactoryClassName)
                || "org.chris.portmapper.router.sbbi.SBBIRouterFactory".equals(routerFactoryClassName)) {
            routerFactoryClassName = JUPnPRouterFactory.class.getName();
        }
        return routerFactoryClassName;
    }

    public void setRouterFactoryClassName(final String routerFactoryClassName) {
        this.routerFactoryClassName = routerFactoryClassName;
    }

    /**
     * Whether jUPnP's chatty discovery messages about non-router UPnP devices
     * on the network (Sonos, Chromecast, NAS, smart-home gear) are suppressed
     * from the in-app log panel and stderr. Defaults to {@code true} — most
     * users don't want to see "Found service of wrong type" twenty times per
     * connect against a typical home network.
     *
     * @return {@code true} if discovery noise is filtered; {@code false} if
     *         every jUPnP log line is shown verbatim.
     */
    public boolean isFilterDiscoveryNoise() {
        return filterDiscoveryNoise;
    }

    public void setFilterDiscoveryNoise(final boolean filterDiscoveryNoise) {
        this.filterDiscoveryNoise = filterDiscoveryNoise;
    }
}
