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

package org.glassfish.tyrus.tests.servlet.basic;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Basic access authentication test.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class BasicAuthTest
        extends TestContainer {

    private static final String CONTEXT_PATH = "/servlet-basic-auth-test";

    public static final String SCHEME = "ws";


    public BasicAuthTest() {
        setContextPath(CONTEXT_PATH);
    }

    @Test
    public void testJdkClientBasicAuth() throws DeploymentException, InterruptedException, IOException {
        System.setProperty("tyrus.test.container.client", "org.glassfish.tyrus.container.jdk.client.JdkClientContainer");
        testBasicAuth();
    }

    @Test
    public void testGrizzlyClientBasicAuth() throws DeploymentException, InterruptedException, IOException {
        System.setProperty("tyrus.test.container.client", "org.glassfish.tyrus.container.grizzly.client.GrizzlyClientContainer");
        testBasicAuth();
    }

    private void testBasicAuth() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(BasicAuthEchoEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientManager client = createClient();

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.HTTP_AUTHENTICATION_USERNAME, "user1");
            client.getProperties().put(ClientProperties.HTTP_AUTHENTICATION_PASSWORD, "password".getBytes("iso-8859-1"));

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertEquals(message, "Do or do not, there is no try.");
                                messageLatch.countDown();
                                System.out.println("We have received a message from access protected server endpoint.");
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, getURI(BasicAuthEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value(), SCHEME));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

}