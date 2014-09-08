/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.tyrus.spi;

import java.net.InetAddress;
import java.util.Map;

/**
 * Facade for handling client operations from containers.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public interface ClientEngine {

    /**
     * Create upgrade request and register {@link TimeoutHandler}.
     *
     * @param timeoutHandler handshake timeout handler. {@link TimeoutHandler#handleTimeout()}
     *                       is invoked if {@link #processResponse(UpgradeResponse, Writer, Connection.CloseListener)}
     *                       is not called within handshake timeout.
     * @return request to be send on the wire or {@code null}, when the request cannot be created. When {@code null} is
     * returned, client should free all resources tied to current connection.
     */
    public UpgradeRequest createUpgradeRequest(TimeoutHandler timeoutHandler);

    /**
     * Process handshake and return {@link ClientUpgradeInfo} with handshake status ({@link ClientUpgradeStatus}).
     *
     * @param upgradeResponse response to be processed.
     * @param writer          used for sending dataframes from client endpoint.
     * @param closeListener   will be called when connection is closed, will be set as listener of returned
     *                        {@link Connection}.
     * @return info with upgrade status.
     * @see #processError(Throwable)
     */
    public ClientUpgradeInfo processResponse(UpgradeResponse upgradeResponse,
                                             final Writer writer,
                                             final Connection.CloseListener closeListener);

    /**
     * Process error.
     * <p/>
     * This method can be called any time when client encounters an error which cannot be handled in the container before
     * {@link ClientUpgradeStatus#SUCCESS} is returned from {@link #processResponse(UpgradeResponse, Writer, Connection.CloseListener)}.
     *
     * @param t encountered error.
     * @see #processResponse(UpgradeResponse, Writer, Connection.CloseListener)
     */
    public void processError(Throwable t);

    /**
     * Indicates to container that handshake timeout was reached.
     */
    public interface TimeoutHandler {
        /**
         * Invoked when timeout is reached. Container is supposed to clean all resources related to {@link ClientEngine}
         * instance.
         */
        public void handleTimeout();
    }

    /**
     * Upgrade process result.
     * <p/>
     * Provides information about upgrade process. There are three possible states which can be reported:
     * <ul>
     * <li>{@link ClientUpgradeStatus#ANOTHER_UPGRADE_REQUEST_REQUIRED}</li>
     * <li>{@link ClientUpgradeStatus#UPGRADE_REQUEST_FAILED}</li>
     * <li>{@link ClientUpgradeStatus#SUCCESS}</li>
     * </ul>
     * <p/>
     * When {@link #getUpgradeStatus()} returns {@link ClientUpgradeStatus#SUCCESS}, client container can create
     * {@link Connection} and start processing read events from the underlying connection and report them to Tyrus
     * runtime.
     * <p/>
     * When {@link #getUpgradeStatus()} returns {@link ClientUpgradeStatus#UPGRADE_REQUEST_FAILED}, client container
     * HAS TO close all resources related to currently processed {@link UpgradeResponse}.
     * <p/>
     * When {@link #getUpgradeStatus()} returns {@link ClientUpgradeStatus#ANOTHER_UPGRADE_REQUEST_REQUIRED}, client
     * container HAS TO close all resources related to currently processed {@link UpgradeResponse}, open new TCP
     * connection and send {@link UpgradeRequest} obtained from method {@link #createUpgradeRequest(TimeoutHandler)}.
     */
    public interface ClientUpgradeInfo {

        /**
         * The name of property containing an {@link InetAddress} object representing an address of the server.
         * <p/>
         * An expected value is {@link InetAddress}.
         * Property can be passed in {@code connectionProperties} map (see {@link #createConnection(Map)}.
         */
        String REMOTE_INET_ADDRESS = "org.glassfish.tyrus.client.spi.remoteInetAddress";

        /**
         * The name of property containing an IP address of the server.
         * <p/>
         * An expected value is {@link String}.
         * Property can be passed in {@code connectionProperties} map (see {@link #createConnection(Map)}.
         */
        String REMOTE_ADDR = "org.glassfish.tyrus.client.spi.remoteAddr";

        /**
         * The name of property containing a hostname of the server.
         * <p/>
         * An expected value is {@link String}.
         * Property can be passed in {@code connectionProperties} map (see {@link #createConnection(Map)}.
         */
        String REMOTE_HOSTNAME = "org.glassfish.tyrus.client.spi.remoteHostName";

        /**
         * The name of property containing a port number of the server.
         * <p/>
         * An expected value is {@link Integer}.
         * Property can be passed in {@code connectionProperties} map (see {@link #createConnection(Map)}.
         */
        String REMOTE_PORT = "org.glassfish.tyrus.client.spi.remotePort";

        /**
         * The name of property containing an {@link InetAddress} object representing an address of the client.
         * <p/>
         * An expected value is {@link InetAddress}.
         * Property can be passed in {@code connectionProperties} map (see {@link #createConnection(Map)}.
         */
        String LOCAL_INET_ADDRESS = "org.glassfish.tyrus.client.spi.localInetAddress";

        /**
         * The name of property containing an IP address of the client.
         * <p/>
         * An expected value is {@link String}.
         * Property can be passed in {@code connectionProperties} map (see {@link #createConnection(Map)}.
         */
        String LOCAL_ADDR = "org.glassfish.tyrus.client.spi.localAddr";

        /**
         * The name of property containing a hostname of the client.
         * <p/>
         * An expected value is {@link String}.
         * Property can be passed in {@code connectionProperties} map (see {@link #createConnection(Map)}.
         */
        String LOCAL_HOSTNAME = "org.glassfish.tyrus.client.spi.localHostName";

        /**
         * The name of property containing a port number of the client.
         * <p/>
         * An expected value is {@link Integer}.
         * Property can be passed in {@code connectionProperties} map (see {@link #createConnection(Map)}.
         */
        String LOCAL_PORT = "org.glassfish.tyrus.client.spi.localPort";

        /**
         * Get {@link ClientUpgradeStatus}.
         *
         * @return {@link ClientUpgradeStatus}.
         */
        ClientUpgradeStatus getUpgradeStatus();

        /**
         * Create new {@link Connection} when {@link #getUpgradeStatus()} returns {@link ClientUpgradeStatus#SUCCESS}.
         *
         * @param connectionProperties connection related properties like remote/local IP addresses, port numbers or hostnames.
         *                             Expected properties are {@link #REMOTE_INET_ADDRESS}, {@link #REMOTE_ADDR}, {@link #REMOTE_HOSTNAME},
         *                             {@link #REMOTE_PORT}, {@link #LOCAL_INET_ADDRESS}, {@link #LOCAL_ADDR}, {@link #LOCAL_HOSTNAME}
         *                             and {@link #LOCAL_PORT}.
         *                             Unknown properties will be ignored.
         * @return new {@link Connection} instance or {@code null}, when {@link #getUpgradeStatus()} does not return
         * {@link ClientUpgradeStatus#}.
         */
        Connection createConnection(Map<String, Object> connectionProperties);
    }

    /**
     * Status of upgrade process.
     * Returned by {@link #processResponse(UpgradeResponse, Writer, Connection.CloseListener)}.
     */
    public enum ClientUpgradeStatus {

        /**
         * Client engine needs to send another request.
         *
         * @see #createUpgradeRequest(TimeoutHandler)
         */
        ANOTHER_UPGRADE_REQUEST_REQUIRED,

        /**
         * Upgrade process failed.
         */
        UPGRADE_REQUEST_FAILED,

        /**
         * Upgrade process was successful.
         *
         * @see ClientUpgradeInfo#createConnection(Map)
         */
        SUCCESS
    }
}
