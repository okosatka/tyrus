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

package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class SessionTimeoutTest extends TestContainer {

    @ServerEndpoint(value = "/timeout3")
    public static class SessionTimeoutEndpoint {
        private final static CountDownLatch onClosedCalled = new CountDownLatch(1);
        private static final long TIMEOUT = 300;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxIdleTimeout(TIMEOUT);
            System.out.println("SessionTimeoutEndpoint checkpoint#1: " + System.currentTimeMillis());
        }

        @OnClose
        public void onClose(CloseReason closeReason) {
            //TYRUS-230
            System.out.println("SessionTimeoutEndpoint checkpoint#2: " + System.currentTimeMillis());
            if (closeReason.getCloseCode() == CloseReason.CloseCodes.CLOSED_ABNORMALLY) {
                onClosedCalled.countDown();
            } else {
                System.out.println("SessionTimeoutEndpoint close code: " + closeReason.getCloseCode().getCode());
            }
        }
    }

    @Test
    public void testSessionTimeout() throws DeploymentException {
        Server server = startServer(SessionTimeoutEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch latch = new CountDownLatch(1);
            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {

                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    //TYRUS-230
                    assertEquals(1000, closeReason.getCloseCode().getCode());
                    latch.countDown();
                }
            }, cec, getURI(SessionTimeoutEndpoint.class));

            assertTrue(latch.await(5, TimeUnit.SECONDS));

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "SessionTimeoutEndpoint");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/servicesessiontimeout")
    public static class ServiceEndpoint {

        @OnMessage
        public String onMessage(String message) throws InterruptedException {
            if (message.equals("SessionTimeoutEndpoint")) {
                if (SessionTimeoutEndpoint.onClosedCalled.await(3, TimeUnit.SECONDS)) {
                    return POSITIVE;
                }
            } else if (message.equals("SessionNoTimeoutEndpoint")) {
                if (!SessionNoTimeoutEndpoint.onClosedCalled.get()) {
                    return POSITIVE;
                }
            } else if (message.equals("SessionTimeoutChangedEndpoint")) {
                if (SessionTimeoutChangedEndpoint.latch.await(1, TimeUnit.SECONDS) && SessionTimeoutChangedEndpoint.closedNormally.get()) {
                    return POSITIVE;
                } else {
                    System.out.println("SessionTimeoutChangedEndpoint latch: " + SessionTimeoutChangedEndpoint.latch.getCount());
                    System.out.println("SessionTimeoutChangedEndpoint closedNormally: " + SessionTimeoutChangedEndpoint.closedNormally.toString());
                }
            }

            return NEGATIVE;
        }
    }

    @ServerEndpoint(value = "/timeout2")
    public static class SessionNoTimeoutEndpoint {
        public static final AtomicBoolean onClosedCalled = new AtomicBoolean(false);
        private static final long TIMEOUT = 400;
        private final AtomicInteger counter = new AtomicInteger(0);

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxIdleTimeout(TIMEOUT);
            onClosedCalled.set(false);
        }

        @OnMessage
        public void onMessage(String message, Session session) {
            System.out.println("Message received: " + message);
            if (counter.incrementAndGet() == 3) {
                try {
                    if (!onClosedCalled.get()) {
                        session.getBasicRemote().sendText(POSITIVE);
                    } else {
                        session.getBasicRemote().sendText(NEGATIVE);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @OnClose
        public void onClose() {
            onClosedCalled.set(true);
        }
    }

    @Test
    public void testSessionNoTimeoutRaised() throws DeploymentException {
        Server server = startServer(SessionNoTimeoutEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch latch = new CountDownLatch(1);

            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println("Client message received");
                            assertEquals(POSITIVE, message);
                            latch.countDown();
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("Nothing");
                        Thread.sleep(250);
                        session.getBasicRemote().sendText("Nothing");
                        Thread.sleep(250);
                        session.getBasicRemote().sendText("Nothing");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(SessionNoTimeoutEndpoint.class));
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/timeout4")
    public static class SessionTimeoutChangedEndpoint {
        public static final CountDownLatch latch = new CountDownLatch(1);
        public static final AtomicBoolean closedNormally = new AtomicBoolean(false);
        private long timeoutSetTime;
        private static final long TIMEOUT1 = 300;
        private static final long TIMEOUT2 = 700;
        private static boolean first = true;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxIdleTimeout(TIMEOUT1);
            timeoutSetTime = System.currentTimeMillis();
        }

        @OnMessage
        public String message(String message, Session session) {
            if (first) {
                session.setMaxIdleTimeout(TIMEOUT2);
                timeoutSetTime = System.currentTimeMillis();
                first = false;
            }
            return "message";
        }

        @OnClose
        public void onClose() {
            final long currentTime = System.currentTimeMillis();
            final boolean inTime = currentTime - timeoutSetTime - TIMEOUT2 < 50;
            closedNormally.set(inTime);
            if (!inTime) {
                System.out.println("Limit exceeded: " + (currentTime - timeoutSetTime - TIMEOUT2));
                System.out.println("currentTime: " + currentTime);
                System.out.println("timeoutSetTime: " + timeoutSetTime);
            }

            latch.countDown();
        }
    }

    @Test
    public void testSessionTimeoutChanged() throws DeploymentException {
        Server server = startServer(SessionTimeoutChangedEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {

                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.getBasicRemote().sendText("Nothing");
                        Thread.sleep(200);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }
            }, cec, getURI(SessionTimeoutChangedEndpoint.class));

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "SessionTimeoutChangedEndpoint");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/timeout1")
    public static class SessionClientTimeoutEndpoint {
        public static final AtomicBoolean clientOnCloseCalled = new AtomicBoolean(false);

    }

    @Test
    public void testSessionClientTimeoutSession() throws DeploymentException {
        Server server = startServer(SessionClientTimeoutEndpoint.class);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);
        SessionClientTimeoutEndpoint.clientOnCloseCalled.set(false);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            Session session = client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void onOpen(Session session) {
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    SessionClientTimeoutEndpoint.clientOnCloseCalled.set(true);
                    onCloseLatch.countDown();
                }
            }, cec, getURI(SessionClientTimeoutEndpoint.class));
            session.setMaxIdleTimeout(200);

            onCloseLatch.await(2, TimeUnit.SECONDS);
            assertTrue(SessionClientTimeoutEndpoint.clientOnCloseCalled.get());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testSessionClientTimeoutSessionOnOpen() throws DeploymentException {
        Server server = startServer(SessionClientTimeoutEndpoint.class);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);
        SessionClientTimeoutEndpoint.clientOnCloseCalled.set(false);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void onOpen(Session session) {
                    session.setMaxIdleTimeout(200);
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    SessionClientTimeoutEndpoint.clientOnCloseCalled.set(true);
                    onCloseLatch.countDown();
                }
            }, cec, getURI(SessionClientTimeoutEndpoint.class));

            onCloseLatch.await(2, TimeUnit.SECONDS);
            assertTrue(SessionClientTimeoutEndpoint.clientOnCloseCalled.get());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testSessionClientTimeoutContainer() throws DeploymentException {
        Server server = startServer(SessionClientTimeoutEndpoint.class);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);
        SessionClientTimeoutEndpoint.clientOnCloseCalled.set(false);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final ClientManager client = createClient();
            client.setDefaultMaxSessionIdleTimeout(200);
            Session session = client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void onOpen(Session session) {
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    SessionClientTimeoutEndpoint.clientOnCloseCalled.set(true);
                    onCloseLatch.countDown();
                }
            }, cec, getURI(SessionClientTimeoutEndpoint.class));

            onCloseLatch.await(2, TimeUnit.SECONDS);
            assertTrue(SessionClientTimeoutEndpoint.clientOnCloseCalled.get());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testSessionTimeoutReset1() throws DeploymentException {
        Server server = startServer(SessionClientTimeoutEndpoint.class);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);
        SessionClientTimeoutEndpoint.clientOnCloseCalled.set(false);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final ClientManager client = createClient();
            client.setDefaultMaxSessionIdleTimeout(1000);
            Session session = client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void onOpen(Session session) {
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    System.out.println(System.currentTimeMillis() + "### !closed " + closeReason);
                    SessionClientTimeoutEndpoint.clientOnCloseCalled.set(true);
                    onCloseLatch.countDown();
                }
            }, cec, getURI(SessionClientTimeoutEndpoint.class));

            assertTrue(session.getMaxIdleTimeout() == 1000);
            session.setMaxIdleTimeout(0);

            assertFalse(onCloseLatch.await(4, TimeUnit.SECONDS));
            assertFalse(SessionClientTimeoutEndpoint.clientOnCloseCalled.get());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testSessionTimeoutReset2() throws DeploymentException {
        Server server = startServer(SessionClientTimeoutEndpoint.class);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);
        SessionClientTimeoutEndpoint.clientOnCloseCalled.set(false);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final ClientManager client = createClient();
            client.setDefaultMaxSessionIdleTimeout(1000);
            Session session = client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void onOpen(Session session) {
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    System.out.println(System.currentTimeMillis() + "### !closed " + closeReason);
                    SessionClientTimeoutEndpoint.clientOnCloseCalled.set(true);
                    onCloseLatch.countDown();
                }
            }, cec, getURI(SessionClientTimeoutEndpoint.class));

            assertTrue(session.getMaxIdleTimeout() == 1000);
            session.setMaxIdleTimeout(-10);

            assertFalse(onCloseLatch.await(4, TimeUnit.SECONDS));
            assertFalse(SessionClientTimeoutEndpoint.clientOnCloseCalled.get());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}