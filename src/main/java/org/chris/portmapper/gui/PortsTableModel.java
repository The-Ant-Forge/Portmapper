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
/**
 *
 */
package org.chris.portmapper.gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import org.chris.portmapper.Messages;
import org.chris.portmapper.model.Protocol;
import org.chris.portmapper.model.SinglePortMapping;

/**
 * The table model for the ports table.
 */
public class PortsTableModel extends AbstractTableModel implements PropertyChangeListener {

    private static final long serialVersionUID = 1L;
    @SuppressWarnings("serial") // List interface is not Serializable; suppress the type-based warning
    private final List<SinglePortMapping> ports;

    public PortsTableModel(final List<SinglePortMapping> ports) {
        this.ports = ports;
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public int getRowCount() {
        return ports.size();
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
        final SinglePortMapping port = ports.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> port.protocol();
            case 1 -> port.externalPort();
            case 2 -> port.internalPort();
            default -> throw new IllegalArgumentException("Column " + columnIndex + " does not exist");
        };
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
        return switch (columnIndex) {
            case 0 -> Protocol.class;
            case 1, 2 -> Integer.class;
            default -> throw new IllegalArgumentException("Column " + columnIndex + " does not exist");
        };
    }

    @Override
    public String getColumnName(final int column) {
        return switch (column) {
            case 0 -> Messages.get("preset_dialog.ports.protocol");
            case 1 -> Messages.get("preset_dialog.ports.external");
            case 2 -> Messages.get("preset_dialog.ports.internal");
            default -> throw new IllegalArgumentException("Column " + column + " does not exist");
        };
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
        return true;
    }

    @Override
    public void setValueAt(final Object value, final int rowIndex, final int columnIndex) {
        final SinglePortMapping port = ports.get(rowIndex);
        // SinglePortMapping is an immutable record; build a new value with the edited field replaced.
        final SinglePortMapping updated = switch (columnIndex) {
            case 0 -> new SinglePortMapping((Protocol) value, port.internalPort(), port.externalPort());
            case 1 -> new SinglePortMapping(port.protocol(), port.internalPort(), (Integer) value);
            case 2 -> new SinglePortMapping(port.protocol(), (Integer) value, port.externalPort());
            default -> throw new IllegalArgumentException("Column " + columnIndex + " does not exist");
        };
        ports.set(rowIndex, updated);
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
        this.fireTableDataChanged();
    }
}
