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
package org.glassfish.tyrus.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class TyrusSessionTest {
    private TyrusEndpointWrapper endpointWrapper;
    private BaseContainer container;

    public TyrusSessionTest() {
        try {
            endpointWrapper = new TyrusEndpointWrapper(EchoEndpoint.class, null, ComponentProviderService.create(), null, null, null, null, null, null, null);
        } catch (DeploymentException e) {
            // do nothing.
        }
    }

    @Before
    public void before() {
        container = createContainer();
    }

    @After
    public void after() {
        container.shutdown();
    }

    @Test
    public void simpleTest() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });

        container.shutdown();
    }


    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlers() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<Reader>() {
            @Override
            public void onMessage(Reader message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleStringHandlers() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlers() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersWithByteArray() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
            @Override
            public void onMessage(byte[] message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleInputStreamHandlers() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multiplePongHandlers() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBasicDecodable() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Whole<TyrusSessionTest>() {
            @Override
            public void onMessage(TyrusSessionTest message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<TyrusSessionTest>() {
            @Override
            public void onMessage(TyrusSessionTest message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlersAsync() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<Reader>() {
            @Override
            public void onMessage(Reader message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleStringHandlersAsync() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersAsync() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Partial<InputStream>() {
            @Override
            public void onMessage(InputStream message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersWithByteArrayAsync() {
        Session session = createSession(container, endpointWrapper);

        session.addMessageHandler(new MessageHandler.Partial<byte[]>() {
            @Override
            public void onMessage(byte[] message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleInputStreamHandlersAsync() {
        Session session = createSession(container, endpointWrapper);


        session.addMessageHandler(new MessageHandler.Partial<InputStream>() {
            @Override
            public void onMessage(InputStream message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<InputStream>() {
            @Override
            public void onMessage(InputStream message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multiplePongHandlersAsync() {
        Session session = createSession(container, endpointWrapper);


        session.addMessageHandler(new MessageHandler.Partial<PongMessage>() {
            @Override
            public void onMessage(PongMessage message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<PongMessage>() {
            @Override
            public void onMessage(PongMessage message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBasicDecodableAsync() {
        Session session = createSession(container, endpointWrapper);


        session.addMessageHandler(new MessageHandler.Partial<TyrusSessionTest>() {
            @Override
            public void onMessage(TyrusSessionTest message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<TyrusSessionTest>() {
            @Override
            public void onMessage(TyrusSessionTest message, boolean last) {
            }
        });
    }

    @Test
    public void getHandlers() {
        Session session = createSession(container, endpointWrapper);

        final MessageHandler.Whole<String> handler1 = new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        };
        final MessageHandler.Whole<ByteBuffer> handler2 = new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        };
        final MessageHandler.Whole<PongMessage> handler3 = new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        };

        session.addMessageHandler(handler1);
        session.addMessageHandler(handler2);
        session.addMessageHandler(handler3);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertTrue(session.getMessageHandlers().contains(handler2));
        assertTrue(session.getMessageHandlers().contains(handler3));
    }

    @Test
    public void removeHandlers() {
        Session session = createSession(container, endpointWrapper);


        final MessageHandler.Partial<String> handler1 = new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        };
        final MessageHandler.Whole<ByteBuffer> handler2 = new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        };
        final MessageHandler.Whole<PongMessage> handler3 = new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        };

        session.addMessageHandler(handler1);
        session.addMessageHandler(handler2);
        session.addMessageHandler(handler3);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertTrue(session.getMessageHandlers().contains(handler2));
        assertTrue(session.getMessageHandlers().contains(handler3));

        session.removeMessageHandler(handler3);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertTrue(session.getMessageHandlers().contains(handler2));
        assertFalse(session.getMessageHandlers().contains(handler3));

        session.removeMessageHandler(handler2);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertFalse(session.getMessageHandlers().contains(handler2));
        assertFalse(session.getMessageHandlers().contains(handler3));
    }

    @Test
    public void idTest() {
        Session session1 = createSession(container, endpointWrapper);
        Session session2 = createSession(container, endpointWrapper);
        Session session3 = createSession(container, endpointWrapper);

        assertFalse(session1.getId().equals(session2.getId()));
        assertFalse(session1.getId().equals(session3.getId()));
        assertFalse(session2.getId().equals(session3.getId()));
    }


    @ServerEndpoint(value = "/echo")
    private static class EchoEndpoint extends Endpoint {

        @Override
        public void onOpen(final Session session, EndpointConfig config) {
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    final String s = EchoEndpoint.this.doThat(message);
                    if (s != null) {
                        try {
                            session.getBasicRemote().sendText(s);
                        } catch (IOException e) {
                            System.out.println("# error");
                            e.printStackTrace();
                        }
                    }
                }
            });
        }

        @OnMessage
        public String doThat(String message) {
            return message;
        }
    }

    @Test
    public void userPropertiesTest() {
        Session session1 = createSession(container, endpointWrapper);
        Session session2 = createSession(container, endpointWrapper);

        final String test1 = "test1";
        final String test2 = "test2";

        session1.getUserProperties().put(test1, test1);
        session2.getUserProperties().put(test2, test2);

        assertNull(session1.getUserProperties().get(test2));
        assertNull(session2.getUserProperties().get(test1));

        assertNotNull(session1.getUserProperties().get(test1));
        assertNotNull(session2.getUserProperties().get(test2));
    }

    private TyrusSession createSession(WebSocketContainer container, TyrusEndpointWrapper endpointWrapper) {
        return new TyrusSession(container, new TestRemoteEndpoint(), endpointWrapper, null, null, false, null, null, null,
                null, new HashMap<String, List<String>>(), null, null, null, null, null, -1, null, null, null, -1, new DebugContext());
    }

    private BaseContainer createContainer() {
        return new BaseContainer() {
            @Override
            public long getDefaultAsyncSendTimeout() {
                return 0;
            }

            @Override
            public void setAsyncSendTimeout(long timeoutmillis) {

            }

            @Override
            public Session connectToServer(Object annotatedEndpointInstance, URI path) throws DeploymentException, IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public Session connectToServer(Class<?> annotatedEndpointClass, URI path) throws DeploymentException, IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public Session connectToServer(Endpoint endpointInstance, ClientEndpointConfig cec, URI path) throws DeploymentException, IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public Session connectToServer(Class<? extends Endpoint> endpointClass, ClientEndpointConfig cec, URI path) throws DeploymentException, IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public long getDefaultMaxSessionIdleTimeout() {
                return 0;
            }

            @Override
            public void setDefaultMaxSessionIdleTimeout(long timeout) {

            }

            @Override
            public int getDefaultMaxBinaryMessageBufferSize() {
                return 0;
            }

            @Override
            public void setDefaultMaxBinaryMessageBufferSize(int max) {

            }

            @Override
            public int getDefaultMaxTextMessageBufferSize() {
                return 0;
            }

            @Override
            public void setDefaultMaxTextMessageBufferSize(int max) {

            }

            @Override
            public Set<Extension> getInstalledExtensions() {
                return Collections.emptySet();
            }
        };
    }


    private static class TestRemoteEndpoint extends TyrusWebSocket {

        private TestRemoteEndpoint() {
            super(new ProtocolHandler(false, null), null);
        }
    }
}