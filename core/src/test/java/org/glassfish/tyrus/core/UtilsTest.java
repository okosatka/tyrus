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

package org.glassfish.tyrus.core;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests Utils
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class UtilsTest {

    @Test
    public void testParseHttpDateRfc1123() {
        try {
            Date date = Utils.parseHttpDate("Sun, 06 Nov 1994 08:49:37 GMT");
            assertNotNull("Date cannot be null", date);
        } catch (ParseException e) {
            fail("Cannot parse valid date");
        }
    }

    @Test
    public void testParseHttpDateRfc1036() {
        try {
            Date date = Utils.parseHttpDate("Sunday, 06-Nov-94 08:49:37 GMT");
            assertNotNull("Date cannot be null", date);
        } catch (ParseException e) {
            fail("Cannot parse valid date");
        }
    }

    @Test
    public void testParseHttpDateAnsiCAsc() {
        try {
            Date date = Utils.parseHttpDate("Sun Nov  6 08:49:37 1994");
            assertNotNull("Date cannot be null", date);
        } catch (ParseException e) {
            fail("Cannot parse valid date");
        }
    }

    @Test
    public void testParseHttpDateFail() {
        try {
            Utils.parseHttpDate("2014-08-08 23:11:22 GMT");
            fail("Invalid date cannot be parsed");
        } catch (ParseException e) {
            // ok
        }
    }

    @Test
    public void testValidateConnectionProperties() throws UnknownHostException {
        assertTrue(Utils.validateConnectionProperties(createValidConnectionProperties()));
    }

    @Test
    public void testValidateConnectionPropertiesInvalidInetAddress() throws UnknownHostException {
        assertFalse(Utils.validateConnectionProperties(createConnectionPropertiesInvalidRemoteInetAddress()));
    }

    @Test
    public void testValidateConnectionPropertiesMissingInetAddress() throws UnknownHostException {
        assertTrue(Utils.validateConnectionProperties(createConnectionPropertiesMissingInetAddress()));
    }

    @Test
    public void testValidateConnectionPropertiesNullInetAddress() throws UnknownHostException {
        assertTrue(Utils.validateConnectionProperties(createConnectionPropertiesNullInetAddress()));
    }

    @Test
    public void testValidateConnectionPropertiesNullHostname() throws UnknownHostException {
        assertFalse(Utils.validateConnectionProperties(createConnectionPropertiesNullHostname()));
    }

    @Test
    public void testValidateConnectionPropertiesMissingPort() throws UnknownHostException {
        assertFalse(Utils.validateConnectionProperties(createConnectionPropertiesMissingPort()));
    }

    private Map<String, Object> createValidConnectionProperties() throws UnknownHostException {
        Map<String, Object> connectionProperties = new HashMap<String, Object>(8);
        connectionProperties.put(TyrusSession.LOCAL_INET_ADDRESS, InetAddress.getByName("127.0.0.1"));
        connectionProperties.put(TyrusSession.LOCAL_ADDR, "127.0.0.1");
        connectionProperties.put(TyrusSession.LOCAL_HOSTNAME, "localhost");
        connectionProperties.put(TyrusSession.LOCAL_PORT, 1);
        connectionProperties.put(TyrusSession.REMOTE_INET_ADDRESS, InetAddress.getByName("127.0.0.1"));
        connectionProperties.put(TyrusSession.REMOTE_ADDR, "127.0.0.1");
        connectionProperties.put(TyrusSession.REMOTE_HOSTNAME, "localhost");
        connectionProperties.put(TyrusSession.REMOTE_PORT, 1);
        return connectionProperties;
    }

    private Map<String, Object> createConnectionPropertiesInvalidRemoteInetAddress() throws UnknownHostException {
        Map<String, Object> connectionProperties = createValidConnectionProperties();
        connectionProperties.put(TyrusSession.LOCAL_INET_ADDRESS, "127.0.0.1");
        return connectionProperties;
    }

    private Map<String, Object> createConnectionPropertiesNullHostname() throws UnknownHostException {
        Map<String, Object> connectionProperties = createValidConnectionProperties();
        connectionProperties.put(TyrusSession.LOCAL_HOSTNAME, null);
        return connectionProperties;
    }

    private Map<String, Object> createConnectionPropertiesNullInetAddress() throws UnknownHostException {
        Map<String, Object> connectionProperties = createValidConnectionProperties();
        connectionProperties.put(TyrusSession.LOCAL_INET_ADDRESS, null);
        return connectionProperties;
    }

    private Map<String, Object> createConnectionPropertiesMissingInetAddress() throws UnknownHostException {
        Map<String, Object> connectionProperties = createValidConnectionProperties();
        connectionProperties.remove(TyrusSession.LOCAL_INET_ADDRESS);
        return connectionProperties;
    }

    private Map<String, Object> createConnectionPropertiesMissingPort() throws UnknownHostException {
        Map<String, Object> connectionProperties = createValidConnectionProperties();
        connectionProperties.remove(TyrusSession.LOCAL_PORT);
        return connectionProperties;
    }

    @Test
    public void testCreateConnectionProperties() {
        assertTrue(Utils.validateConnectionProperties(Utils.getConnectionProperties(new InetSocketAddress(8080), new InetSocketAddress(8080))));
    }
}
