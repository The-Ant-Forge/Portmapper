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
package org.chris.portmapper.router.dummy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;

import org.chris.portmapper.model.PortMapping;
import org.chris.portmapper.model.Protocol;
import org.chris.portmapper.router.RouterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behavioral tests for {@link DummyRouter} against the {@code IRouter} contract.
 * Acts as a safety net before backend swaps (Cling -> jUPnP, etc.): if a future
 * router implementation regresses any of this behavior, these tests catch it.
 *
 * Note: {@link DummyRouter#getPortMappings()} contains a deliberate 3-second
 * sleep to simulate router latency in the GUI. Tests that hit it pay that cost.
 */
class TestDummyRouter {

    private static final String ROUTER_NAME = "TestRouter";
    private DummyRouter router;

    @BeforeEach
    void setUp() {
        router = new DummyRouter(ROUTER_NAME);
    }

    @Test
    void getNameReturnsConstructorName() {
        assertEquals(ROUTER_NAME, router.getName());
    }

    @Test
    void getExternalIPAddressReturnsFixedString() throws RouterException {
        assertEquals("DummyExternalIP", router.getExternalIPAddress());
    }

    @Test
    void getInternalHostNameReturnsFixedString() {
        assertEquals("DummyInternalHostName", router.getInternalHostName());
    }

    @Test
    void getInternalPortReturnsFixedValue() throws RouterException {
        assertEquals(42, router.getInternalPort());
    }

    @Test
    void getLocalHostAddressReturnsFixedString() throws RouterException {
        assertEquals("DummyLocalhostAddress", router.getLocalHostAddress());
    }

    @Test
    void disconnectDoesNotThrow() {
        assertDoesNotThrow(router::disconnect);
    }

    @Test
    void closeDoesNotThrow() {
        assertDoesNotThrow(router::close);
    }

    @Test
    void logRouterInfoDoesNotThrow() throws RouterException {
        router.logRouterInfo();
    }

    @Test
    void getPortMappingsReturnsSeededEntries() throws RouterException {
        final Collection<PortMapping> mappings = router.getPortMappings();
        assertNotNull(mappings);
        assertEquals(3, mappings.size(), "DummyRouter seeds three sample mappings on construction");
    }

    @Test
    void addPortMappingExpandsTheCollection() throws RouterException {
        final PortMapping added = new PortMapping(Protocol.TCP, null, 8080, "10.0.0.5", 8080, "test-add");
        router.addPortMapping(added);
        final Collection<PortMapping> mappings = router.getPortMappings();
        assertEquals(4, mappings.size());
        assertTrue(mappings.contains(added));
    }

    @Test
    void addPortMappingsExpandsTheCollectionByBatchSize() throws RouterException {
        final List<PortMapping> batch = List.of(
                new PortMapping(Protocol.TCP, null, 9000, "10.0.0.5", 9000, "batch-1"),
                new PortMapping(Protocol.UDP, null, 9001, "10.0.0.5", 9001, "batch-2"));
        router.addPortMappings(batch);
        assertEquals(5, router.getPortMappings().size());
    }

    @Test
    void removeMappingShrinksTheCollection() throws RouterException {
        final Collection<PortMapping> before = router.getPortMappings();
        final PortMapping victim = before.iterator().next();
        router.removeMapping(victim);
        final Collection<PortMapping> after = router.getPortMappings();
        assertEquals(2, after.size());
        assertTrue(!after.contains(victim));
    }
}
