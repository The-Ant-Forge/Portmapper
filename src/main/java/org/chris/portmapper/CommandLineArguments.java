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

import org.chris.portmapper.model.Protocol;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/**
 * Command-line argument parser. Migrated from args4j (end-of-life 2018) to
 * picocli 4.7.x. The CLI contract — single-dash option names like
 * {@code -add}, {@code -list}, {@code -h}/{@code -help}, mutual exclusion
 * among the mode flags, and the {@code -add}/{@code -delete} required-companions
 * rule — is preserved verbatim.
 */
@Command(
        name = "portmapper",
        description = "UPnP PortMapper - manage port mappings on a UPnP router",
        sortOptions = false,
        footer = {
                "",
                "Example: java -jar PortMapper.jar -add -externalPort 22 -internalPort 22 [-ip <ip-addr>] -description desc"
        }
)
public class CommandLineArguments {

    /**
     * The mutually exclusive mode flags (only one may be supplied). picocli enforces
     * this at parse time and throws {@link ParameterException} for any combination.
     */
    @ArgGroup(exclusive = true)
    private Mode mode = new Mode();

    private static final class Mode {
        @Option(names = { "-h", "-help" }, description = "Print usage help")
        private boolean printHelp;

        @Option(names = "-gui", description = "Start graphical user interface (default)")
        private boolean startGui;

        @Option(names = "-add", description = "Add a new port mapping")
        private boolean addPortMapping;

        @Option(names = "-delete", description = "Delete a port mapping")
        private boolean deletePortMapping;

        @Option(names = "-info", description = "Print router info")
        private boolean printInfo;

        @Option(names = "-list", description = "Print existing port mappings")
        private boolean listPortMappings;
    }

    @Option(names = "-ip", description = "Internal IP of the port mapping (default: localhost)")
    private String internalIp;

    @Option(names = "-internalPort", description = "Internal port of the port mapping")
    private Integer internalPort;

    @Option(names = "-externalPort", description = "External port of the port mapping")
    private Integer externalPort;

    @Option(names = "-protocol", description = "Protocol of the port mapping (TCP or UDP)")
    private Protocol protocol;

    @Option(names = "-description", description = "Description of the port mapping")
    private String description;

    @Option(names = "-lib", description = "UPnP library to use")
    private String upnpLib;

    @Option(names = "-routerIndex", description = "Router index if more than one is found (zero-based)")
    private Integer routerIndex;

    private final CommandLine commandLine;

    @SuppressWarnings("this-escape")
    public CommandLineArguments() {
        this.commandLine = new CommandLine(this);
        this.commandLine.setUsageHelpWidth(80);
    }

    /**
     * Parse the command-line arguments into this object's fields.
     *
     * @param args the command-line arguments.
     * @return {@code true} on success; {@code false} if parsing failed or a
     *         dependency constraint was violated. The error message and usage
     *         banner are printed to {@code System.err} in the false case.
     */
    public boolean parse(final String[] args) {
        try {
            commandLine.parseArgs(args);
            return validateConstraints();
        } catch (final ParameterException e) {
            System.err.println(e.getMessage());
            printHelp();
            return false;
        }
    }

    /**
     * Apply the cross-option dependency rules that args4j's {@code depends}
     * attribute used to enforce declaratively. picocli has no direct equivalent;
     * post-parse validation is the documented idiom.
     *
     * @return {@code true} if all dependencies are satisfied.
     */
    private boolean validateConstraints() {
        if (isAddPortMapping() && (internalPort == null || externalPort == null || protocol == null)) {
            System.err.println("Error: -add requires -internalPort, -externalPort and -protocol");
            printHelp();
            return false;
        }
        if (isDeletePortMapping() && (externalPort == null || protocol == null)) {
            System.err.println("Error: -delete requires -externalPort and -protocol");
            printHelp();
            return false;
        }
        return true;
    }

    public void printHelp() {
        commandLine.usage(System.err);
    }

    public boolean isPrintHelp() {
        return mode != null && mode.printHelp;
    }

    public boolean isStartGui() {
        return mode != null && mode.startGui;
    }

    public boolean isAddPortMapping() {
        return mode != null && mode.addPortMapping;
    }

    public boolean isDeletePortMapping() {
        return mode != null && mode.deletePortMapping;
    }

    public boolean isPrintInfo() {
        return mode != null && mode.printInfo;
    }

    public boolean isListPortMappings() {
        return mode != null && mode.listPortMappings;
    }

    public String getInternalIp() {
        return internalIp;
    }

    public int getInternalPort() {
        return internalPort == null ? 0 : internalPort;
    }

    public int getExternalPort() {
        return externalPort == null ? 0 : externalPort;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getUpnpLib() {
        return upnpLib;
    }

    public Integer getRouterIndex() {
        return routerIndex;
    }

    public String getDescription() {
        return description;
    }
}
