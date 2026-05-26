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

import java.util.ArrayList;
import java.util.Collection;

import javax.swing.table.AbstractTableModel;

import org.chris.portmapper.Messages;
import org.chris.portmapper.model.PortMapping;

public class PortMappingsTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private final transient ArrayList<PortMapping> mappings = new ArrayList<>();

    public PortMappingsTableModel() {
        // No state to initialize beyond the field default; constructor exists for clarity.
    }

    public void setMappings(final Collection<PortMapping> mappings) {
        this.mappings.clear();
        this.mappings.addAll(mappings);
        super.fireTableDataChanged();
    }

    @Override
    public int getColumnCount() {
        return 6;
    }

    @Override
    public int getRowCount() {
        return mappings.size();
    }

    public PortMapping getPortMapping(final int index) {
        return mappings.get(index);
    }

    @Override
    public Object getValueAt(final int row, final int col) {
        final PortMapping mapping = mappings.get(row);
        return switch (col) {
            case 0 -> mapping.protocol();
            case 1 -> mapping.remoteHost() != null ? mapping.remoteHost() : "";
            case 2 -> mapping.externalPort();
            case 3 -> mapping.internalClient();
            case 4 -> mapping.internalPort();
            case 5 -> mapping.description();
            default -> throw new IllegalArgumentException("Column " + col + " does not exist");
        };
    }

    @Override
    public String getColumnName(final int col) {
        return switch (col) {
            case 0 -> Messages.get("mainFrame.mappings.protocol");
            case 1 -> Messages.get("mainFrame.mappings.remote_host");
            case 2 -> Messages.get("mainFrame.mappings.external_port");
            case 3 -> Messages.get("mainFrame.mappings.internal_client");
            case 4 -> Messages.get("mainFrame.mappings.internal_port");
            case 5 -> Messages.get("mainFrame.mappings.description");
            default -> throw new IllegalArgumentException("Column " + col + " does not exist");
        };
    }
}
