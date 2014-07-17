/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.container.jdk.client;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;

import org.glassfish.tyrus.core.CloseReasons;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.spi.ClientEngine;
import org.glassfish.tyrus.spi.ClientEngine.TimeoutHandler;
import org.glassfish.tyrus.spi.CompletionHandler;
import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.Connection.CloseListener;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.Writer;

/**
 * A filter that interacts with Tyrus SPI and handles proxy.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class ClientFilter extends Filter {

    private static final Logger LOGGER = Logger.getLogger(ClientFilter.class.getName());

    private final ClientEngine engine;
    private final URI uri;
    private final HttpResponseParser responseParser = new HttpResponseParser();
    private final Map<String, String> proxyHeaders;
    private final Callable<Void> jdkConnector;

    private volatile boolean proxy;

    private volatile Connection wsConnection;
    private volatile boolean connectedToProxy = false;


    /**
     * Constructor.
     *
     * @param engine       client engine instance.
     * @param uri          URI to be used for creating {@link org.glassfish.tyrus.spi.UpgradeRequest}.
     * @param proxyHeaders map representing headers to be added to request sent to proxy (HTTP CONNECT).
     * @param jdkConnector callback to connecting with modified {@link UpgradeRequest} if necessary.
     */
    ClientFilter(ClientEngine engine, URI uri, Map<String, String> proxyHeaders, Callable<Void> jdkConnector) {
        this.engine = engine;
        this.uri = uri;
        this.proxyHeaders = proxyHeaders;
        this.jdkConnector = jdkConnector;
    }

    @Override
    public void onConnect(final Filter downstreamFilter) {

        final UpgradeRequest upgradeRequest = engine.createUpgradeRequest(uri, new TimeoutHandler() {
            @Override
            public void handleTimeout() {
                downstreamFilter.close();
            }
        });
        final JdkUpgradeRequest handshakeUpgradeRequest = getJdkUpgradeRequest(upgradeRequest, downstreamFilter);

        sendRequest(downstreamFilter, handshakeUpgradeRequest);
    }

    private void sendRequest(final Filter downstreamFilter, JdkUpgradeRequest handshakeUpgradeRequest) {
        downstreamFilter.write(HttpRequestBuilder.build(handshakeUpgradeRequest), new CompletionHandler<ByteBuffer>() {
            @Override
            public void failed(Throwable throwable) {
                closeConnection(downstreamFilter);
            }
        });
    }

    private JdkUpgradeRequest getJdkUpgradeRequest(final UpgradeRequest upgradeRequest, final Filter downstreamFilter) {
        final JdkUpgradeRequest handshakeUpgradeRequest;
        if (!proxy) {
            downstreamFilter.startSsl();
            handshakeUpgradeRequest = createHandshakeUpgradeRequest(upgradeRequest);
        } else {
            handshakeUpgradeRequest = createProxyUpgradeRequest(upgradeRequest);
        }
        return handshakeUpgradeRequest;
    }

    @Override
    public void onRead(final Filter downstreamFilter, ByteBuffer data) {

        if (wsConnection == null) {

            responseParser.appendData(data);

            if (!responseParser.isComplete()) {
                return;
            }

            TyrusUpgradeResponse tyrusUpgradeResponse;

            try {
                tyrusUpgradeResponse = responseParser.parseUpgradeResponse();
            } catch (ParseException e) {
                LOGGER.log(Level.SEVERE, "Parsing HTTP handshake response failed", e);
                closeConnection(downstreamFilter);
                return;
            } finally {
                responseParser.clear();
            }

            if (proxy && !connectedToProxy) {
                if (tyrusUpgradeResponse.getStatus() != 200) {
                    LOGGER.log(Level.SEVERE, "Could not connect to proxy: " + tyrusUpgradeResponse.getStatus());
                    closeConnection(downstreamFilter);
                    return;
                }
                connectedToProxy = true;
                downstreamFilter.startSsl();
                sendRequest(downstreamFilter, createHandshakeUpgradeRequest(engine.createUpgradeRequest(uri, null)));
                return;
            }

            JdkWriter writer = new JdkWriter(downstreamFilter);

            ClientEngine.ClientUpgradeInfo clientUpgradeInfo = engine.processResponse(
                    tyrusUpgradeResponse,
                    writer,
                    new CloseListener() {

                        @Override
                        public void close(CloseReason reason) {
                            closeConnection(downstreamFilter);
                        }
                    }
            );

            switch (clientUpgradeInfo.getUpgradeStatus()) {
                case ANOTHER_UPGRADE_REQUEST_REQUIRED:
                    closeConnection(downstreamFilter);
                    try {
                        jdkConnector.call();
                    } catch (Exception e) {
                        // TODO: we might want to pass this exception directly to the user (to be thrown
                        // TODO: as result of "connectToServer" method call.
                        LOGGER.log(Level.WARNING, LocalizationMessages.CLIENT_CANNOT_CONNECT(uri.toString()));
                    }
                    break;
                case SUCCESS:
                    wsConnection = clientUpgradeInfo.createConnection();
                    break;
                case UPGRADE_REQUEST_FAILED:
                    closeConnection(downstreamFilter);
                    break;
                default:
                    break;
            }
        } else {
            wsConnection.getReadHandler().handle(data);
        }
    }

    @Override
    public void onConnectionClosed() {
        if (wsConnection == null) {
            return;
        }
        wsConnection.close(CloseReasons.CLOSED_ABNORMALLY.getCloseReason());
    }

    /**
     * Set proxy flag.
     * <p/>
     * * @param proxy  {@code true} if the connection will be established via proxy, {@code false} otherwise.
     */
    public void setProxy(boolean proxy) {
        this.proxy = proxy;
    }

    private void closeConnection(Filter downstreamFilter) {
        downstreamFilter.close();
    }

    private static class JdkWriter extends Writer {

        private final Filter downstreamFilter;

        JdkWriter(Filter downstreamFilter) {
            this.downstreamFilter = downstreamFilter;
        }

        @Override
        public void close() throws IOException {
            downstreamFilter.close();
        }

        @Override
        public void write(ByteBuffer buffer, CompletionHandler<ByteBuffer> completionHandler) {
            downstreamFilter.write(buffer, completionHandler);
        }
    }

    private JdkUpgradeRequest createHandshakeUpgradeRequest(final UpgradeRequest upgradeRequest) {
        return new JdkUpgradeRequest(upgradeRequest) {

            @Override
            public String getHttpMethod() {
                return "GET";
            }

            @Override
            public String getRequestUri() {
                StringBuilder sb = new StringBuilder();
                final URI uri = URI.create(upgradeRequest.getRequestUri());
                sb.append(uri.getPath());
                final String query = uri.getQuery();
                if (query != null) {
                    sb.append('?').append(query);
                }
                if (sb.length() == 0) {
                    sb.append('/');
                }
                return sb.toString();
            }
        };
    }

    private JdkUpgradeRequest createProxyUpgradeRequest(final UpgradeRequest upgradeRequest) {
        return new JdkUpgradeRequest(upgradeRequest) {

            @Override
            public String getHttpMethod() {
                return "CONNECT";
            }

            @Override
            public String getRequestUri() {
                URI uri = URI.create(upgradeRequest.getRequestUri());
                final int requestPort = uri.getPort() == -1 ? (uri.getScheme().equals("wss") ? 443 : 80) : uri.getPort();
                return String.format("%s:%d", uri.getHost(), requestPort);
            }

            @Override
            public Map<String, List<String>> getHeaders() {
                URI uri = URI.create(upgradeRequest.getRequestUri());
                Map<String, List<String>> headers = new HashMap<>();
                if (proxyHeaders != null) {
                    for (Map.Entry<String, String> entry : proxyHeaders.entrySet()) {
                        headers.put(entry.getKey(), Collections.singletonList(entry.getValue()));
                    }
                }
                headers.put("Host", Collections.singletonList(uri.getHost()));
                headers.put("ProxyConnection", Collections.singletonList("keep-alive"));
                headers.put("Connection", Collections.singletonList("keep-alive"));
                return headers;
            }
        };
    }
}
