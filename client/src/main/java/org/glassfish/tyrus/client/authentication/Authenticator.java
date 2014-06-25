/*
 *
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

package org.glassfish.tyrus.client.authentication;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

/**
 * Utility class which find right authentication implementation of {@link org.glassfish.tyrus.client.authentication.AuthHeaderGenerator}.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class Authenticator {

    /**
     * Encoding used for authentication calculations.
     */
    static final Charset CHARACTER_SET = Charset.forName("iso-8859-1");

    private final Set<Class<? extends AuthHeaderGenerator>> generators = new HashSet<Class<? extends AuthHeaderGenerator>>();

    /**
     * Register HTTP authentication header generators.
     */
    public Authenticator() {
        generators.add(BasicAuthHeaderGenerator.class);
    }

    /**
     * Add {@code Authorization} header into given request.
     * <p/>
     * Header is constructed
     *
     * @param request     {@link org.glassfish.tyrus.spi.UpgradeRequest} to enrich.
     * @param response    {@link org.glassfish.tyrus.spi.UpgradeResponse} containing {@code WWW-Authenticate} header
     *                    with authenticate parameters.
     * @param credentials username/password credentials.
     * @throws AuthenticationException thrown when unsupported auth scheme is required.
     */
    public void addAuthHeader(UpgradeRequest request, UpgradeResponse response, Credentials credentials) throws AuthenticationException {
        AuthHeaderGenerator generator = findGenerator(response);
        String headerValue = generator.getAuthorizationHeader(credentials);
        request.getHeaders().put(UpgradeRequest.AUTHORIZATION, Collections.singletonList(headerValue));
    }

    private AuthHeaderGenerator findGenerator(UpgradeResponse response) throws AuthenticationException {
        for (Class<? extends AuthHeaderGenerator> generator : generators) {
            AuthHeaderGenerator instance;
            try {
                instance = generator.newInstance();
            } catch (InstantiationException e) {
                throw new AuthenticationException(LocalizationMessages.AUTHENTICATION_CREATE_AUTH_HEADER_FAILED());
            } catch (IllegalAccessException e) {
                throw new AuthenticationException(LocalizationMessages.AUTHENTICATION_CREATE_AUTH_HEADER_FAILED());
            }
            if (instance.isSuitable(response)) {
                return instance;
            }
        }
        throw new AuthenticationException(LocalizationMessages.AUTHENTICATION_CREATE_AUTH_HEADER_FAILED());
    }

    /**
     * Extract credentials into {@link org.glassfish.tyrus.client.authentication.Authenticator.Credentials}.
     * <p/>
     * Credentials are given to the client in container properties.
     *
     * @param username as a {@link java.lang.String}.
     * @param password as a {@code byte[]} or {@link java.lang.String}.
     * @return {@link org.glassfish.tyrus.client.authentication.Authenticator.Credentials} instance or {@code null}
     * if given {@code username} or {@code password} is null or {@code username} is empty.
     * @throws AuthenticationException if password are different type then {@code byte[]} or {@link java.lang.String}.
     */
    public static Credentials extractCredentials(String username, Object password) throws AuthenticationException {
        if (username != null && !username.equals("") && password != null) {
            byte[] pwdBytes;
            if (password instanceof byte[]) {
                pwdBytes = (byte[]) password;
            } else if (password instanceof String) {
                pwdBytes = ((String) password).getBytes(CHARACTER_SET);
            } else {
                throw new AuthenticationException(LocalizationMessages.AUTHENTICATION_CREDENTIALS_PASSWORD_UNSUPPORTED());
            }
            return new Credentials(username, pwdBytes);
        }
        return null;
    }

    /**
     * Credentials (username + password).
     */
    public static class Credentials {
        private final String username;
        private final byte[] password;


        /**
         * Create a new credentials from username and password as byte array.
         *
         * @param username Username.
         * @param password Password as byte array.
         */
        public Credentials(String username, byte[] password) {
            this.username = username;
            this.password = password;
        }

        /**
         * Return username.
         *
         * @return username.
         */
        public String getUsername() {
            return username;
        }

        /**
         * Return password as byte array.
         *
         * @return Password string in byte array representation.
         */
        public byte[] getPassword() {
            return password;
        }
    }
}
