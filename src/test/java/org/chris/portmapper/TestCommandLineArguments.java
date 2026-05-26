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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.chris.portmapper.model.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CommandLineArguments}. Pins the CLI contract: which flag
 * combinations are valid, which option types coerce correctly, and which
 * combinations are rejected by args4j's {@code forbids}/{@code depends}.
 */
class TestCommandLineArguments {

    private CommandLineArguments args;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void captureStderr() {
        originalErr = System.err;
        capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr));
        args = new CommandLineArguments();
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(originalErr);
    }

    @Test
    void emptyArgsParseSuccessfullyWithAllFlagsFalse() {
        assertTrue(args.parse(new String[] {}));
        assertFalse(args.isPrintHelp());
        assertFalse(args.isStartGui());
        assertFalse(args.isAddPortMapping());
        assertFalse(args.isDeletePortMapping());
        assertFalse(args.isPrintInfo());
        assertFalse(args.isListPortMappings());
        assertNull(args.getInternalIp());
        assertNull(args.getDescription());
        assertNull(args.getUpnpLib());
        assertNull(args.getRouterIndex());
        assertNull(args.getProtocol());
    }

    @Test
    void shortHelpFlag() {
        assertTrue(args.parse(new String[] { "-h" }));
        assertTrue(args.isPrintHelp());
    }

    @Test
    void longHelpFlag() {
        assertTrue(args.parse(new String[] { "-help" }));
        assertTrue(args.isPrintHelp());
    }

    @Test
    void guiFlag() {
        assertTrue(args.parse(new String[] { "-gui" }));
        assertTrue(args.isStartGui());
        assertFalse(args.isAddPortMapping());
    }

    @Test
    void infoFlag() {
        assertTrue(args.parse(new String[] { "-info" }));
        assertTrue(args.isPrintInfo());
    }

    @Test
    void listFlag() {
        assertTrue(args.parse(new String[] { "-list" }));
        assertTrue(args.isListPortMappings());
    }

    @Test
    void addRequiresInternalAndExternalPortAndProtocol() {
        assertFalse(args.parse(new String[] { "-add" }));
        assertFalse(args.parse(new String[] { "-add", "-internalPort", "80" }));
        assertFalse(args.parse(new String[] { "-add", "-internalPort", "80", "-externalPort", "80" }));
    }

    @Test
    void addParsesAllRequiredArgs() {
        assertTrue(args.parse(new String[] {
                "-add", "-internalPort", "8080", "-externalPort", "80", "-protocol", "TCP" }));
        assertTrue(args.isAddPortMapping());
        assertEquals(8080, args.getInternalPort());
        assertEquals(80, args.getExternalPort());
        assertEquals(Protocol.TCP, args.getProtocol());
    }

    @Test
    void deleteRequiresExternalPortAndProtocol() {
        assertFalse(args.parse(new String[] { "-delete" }));
        assertFalse(args.parse(new String[] { "-delete", "-externalPort", "80" }));
    }

    @Test
    void deleteParsesArgs() {
        assertTrue(args.parse(new String[] { "-delete", "-externalPort", "443", "-protocol", "UDP" }));
        assertTrue(args.isDeletePortMapping());
        assertEquals(443, args.getExternalPort());
        assertEquals(Protocol.UDP, args.getProtocol());
    }

    @Test
    void addAndDeleteAreMutuallyExclusive() {
        assertFalse(args.parse(new String[] {
                "-add", "-delete", "-internalPort", "80", "-externalPort", "80", "-protocol", "TCP" }));
    }

    @Test
    void addAndGuiAreMutuallyExclusive() {
        assertFalse(args.parse(new String[] {
                "-add", "-gui", "-internalPort", "80", "-externalPort", "80", "-protocol", "TCP" }));
    }

    @Test
    void listAndInfoAreMutuallyExclusive() {
        assertFalse(args.parse(new String[] { "-list", "-info" }));
    }

    @Test
    void protocolAcceptsBothTcpAndUdp() {
        assertTrue(args.parse(new String[] {
                "-add", "-internalPort", "80", "-externalPort", "80", "-protocol", "UDP" }));
        assertEquals(Protocol.UDP, args.getProtocol());
    }

    @Test
    void libFlagCapturesString() {
        assertTrue(args.parse(new String[] {
                "-list", "-lib", "org.chris.portmapper.router.weupnp.WeUPnPRouterFactory" }));
        assertEquals("org.chris.portmapper.router.weupnp.WeUPnPRouterFactory", args.getUpnpLib());
    }

    @Test
    void routerIndexCapturesInteger() {
        assertTrue(args.parse(new String[] { "-list", "-routerIndex", "2" }));
        assertEquals(Integer.valueOf(2), args.getRouterIndex());
    }

    @Test
    void descriptionAndIpAreOptional() {
        assertTrue(args.parse(new String[] {
                "-add", "-internalPort", "80", "-externalPort", "80", "-protocol", "TCP",
                "-ip", "192.168.1.5", "-description", "web server" }));
        assertEquals("192.168.1.5", args.getInternalIp());
        assertEquals("web server", args.getDescription());
    }

    @Test
    void unknownFlagFailsCleanly() {
        assertFalse(args.parse(new String[] { "-unknown" }));
    }
}
