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

package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;

import org.glassfish.tyrus.core.TyrusSession;
import org.glassfish.tyrus.core.coder.CoderAdapter;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.spi.ClientEngine;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class SessionInfoTest extends TestContainer {

    @ServerEndpoint(value = "/info")
    public static class EchoEndpoint {
        @OnOpen
        public void onOpen(Session session) throws IOException {
            session.getBasicRemote().sendText("onOpen");
        }

        @OnMessage
        public String onMessage(Session session, String message) throws UnknownHostException {
            TyrusSession tyrusSession = (TyrusSession) session;
            printSessionInfo(tyrusSession, "SERVER");
            if (message.equals("get info")) {
                return String.valueOf(checkSessionInfoNotNull(tyrusSession));
            }
            return null;
        }
    }

    @Test
    public void testSessionInfoNotNull() throws DeploymentException {
        Server server = startServer(EchoEndpoint.class);

        final CountDownLatch infoOnClientLatch = new CountDownLatch(1);
        final CountDownLatch infoOnServerLatch = new CountDownLatch(1);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            createClient().connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    TyrusSession tyrusSession = (TyrusSession) session;

                    if (checkSessionInfoNotNull(tyrusSession)) {
                        infoOnClientLatch.countDown();
                        try {
                            session.getBasicRemote().sendText("get info");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    printSessionInfo(tyrusSession, "CLIENT");

                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String s) {
                            System.out.println(s);
                            if (s.equals("true")) {
                                infoOnServerLatch.countDown();
                            }
                        }
                    });
                }
            }, cec, getURI(EchoEndpoint.class));

            assertTrue("Tyrus session info is missing on client-side", infoOnClientLatch.await(1, TimeUnit.SECONDS));
            assertTrue("Tyrus session info is missing on server-side", infoOnServerLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/cross-check", encoders = {JsonEncoder.class})
    public static class JsonEndpoint {
        @OnOpen
        public void onOpen(Session session) throws IOException, EncodeException {
            TyrusSession tyrusSession = (TyrusSession) session;
            printSessionInfo(tyrusSession, "SERVER");
            JsonObject jsonObject = Json.createObjectBuilder().add(ClientEngine.ClientUpgradeInfo.LOCAL_ADDR, tyrusSession.getLocalAddr())
                    .add(ClientEngine.ClientUpgradeInfo.LOCAL_PORT, tyrusSession.getLocalPort())
                    .add(ClientEngine.ClientUpgradeInfo.REMOTE_ADDR, tyrusSession.getRemoteAddr())
                    .add(ClientEngine.ClientUpgradeInfo.REMOTE_PORT, tyrusSession.getRemotePort()).build();
            System.out.println(jsonObject);
            session.getBasicRemote().sendObject(jsonObject);
        }
    }

    @Test
    public void testCrossCheck() throws DeploymentException {
        Server server = startServer(JsonEndpoint.class);

        final CountDownLatch crossCheckPortLatch = new CountDownLatch(1);
        final CountDownLatch crossCheckIPAdressLatch = new CountDownLatch(1);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().decoders(Collections.<Class<? extends Decoder>>singletonList(JsonDecoder.class)).build();

            createClient().connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    final TyrusSession tyrusSession = (TyrusSession) session;

                    printSessionInfo(tyrusSession, "CLIENT");

                    session.addMessageHandler(new MessageHandler.Whole<JsonObject>() {
                        @Override
                        public void onMessage(JsonObject info) {
                            if (info.getString(ClientEngine.ClientUpgradeInfo.LOCAL_ADDR).equals(tyrusSession.getRemoteAddr())
                                    && info.getString(ClientEngine.ClientUpgradeInfo.REMOTE_ADDR).equals(tyrusSession.getLocalAddr())) {
                                crossCheckIPAdressLatch.countDown();
                            }
                            if (info.getInt(ClientEngine.ClientUpgradeInfo.LOCAL_PORT) == tyrusSession.getRemotePort()
                                    && info.getInt(ClientEngine.ClientUpgradeInfo.REMOTE_PORT) == tyrusSession.getLocalPort()) {
                                crossCheckPortLatch.countDown();
                            }
                        }
                    });
                }
            }, cec, getURI(JsonEndpoint.class));

            assertTrue("Remote vs local IP addresses on server and client do not fit", crossCheckIPAdressLatch.await(1, TimeUnit.SECONDS));
            assertTrue("Remote vs local port numbers on server and client do not fit", crossCheckPortLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    private static boolean checkSessionInfoNotNull(TyrusSession tyrusSession) {
        return (tyrusSession.getRemoteInetAddress() != null && tyrusSession.getRemoteAddr() != null
                && tyrusSession.getRemoteHostName() != null && tyrusSession.getRemotePort() != -1
                && tyrusSession.getLocalInetAddress() != null && tyrusSession.getLocalAddr() != null
                && tyrusSession.getLocalHostName() != null && tyrusSession.getLocalPort() != -1);
    }

    private static void printSessionInfo(TyrusSession tyrusSession, String prefix) {
        InetAddress remoteInetAddress = tyrusSession.getRemoteInetAddress();
        System.out.println(prefix + " remoteInetAddress.getAddress(): " + remoteInetAddress.getHostAddress());
        System.out.println(prefix + " remoteAddr: " + tyrusSession.getRemoteAddr());
        System.out.println(prefix + " remoteHost: " + tyrusSession.getRemoteHostName());
        System.out.println(prefix + " remotePort: " + tyrusSession.getRemotePort());
        InetAddress localInetAddress = tyrusSession.getLocalInetAddress();
        System.out.println(prefix + " localInetAddress.getHostAddress(): " + localInetAddress.getHostAddress());
        System.out.println(prefix + " localAddr: " + tyrusSession.getLocalAddr());
        System.out.println(prefix + " localName: " + tyrusSession.getLocalHostName());
        System.out.println(prefix + " localPort: " + tyrusSession.getLocalPort());
    }

    public static class JsonEncoder extends CoderAdapter implements Encoder.Text<JsonObject> {
        @Override
        public String encode(JsonObject o) throws EncodeException {
            return o.toString();
        }
    }

    public static class JsonDecoder extends CoderAdapter implements Decoder.Text<JsonObject> {
        @Override
        public JsonObject decode(String s) throws DecodeException {
            try {
                JsonObject jsonObject = Json.createReader(new StringReader(s)).readObject();
                return jsonObject;
            } catch (JsonException je) {
                throw new DecodeException(s, "JSON not decoded", je);
            }
        }

        @Override
        public boolean willDecode(String s) {
            return true;
        }
    }
}
