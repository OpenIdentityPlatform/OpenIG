/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.jwt;

import static java.lang.String.*;
import static org.forgerock.openig.jwt.JwtCookieSession.*;
import static org.forgerock.openig.util.JsonValueUtil.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;

import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.forgerock.http.Session;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.http.SessionManager;

/**
 * A JwtSessionFactory is responsible to configure and create a {@link JwtCookieSession}.
 *
 * <pre>
 *     {
 *         "name": "JwtSession",
 *         "type": "JwtSession",
 *         "config": {
 *             "keystore": "Ref To A KeyStore",
 *             "alias": "PrivateKey Alias",
 *             "password": "KeyStore/Key Password",
 *             "cookieName": "OpenIG"
 *         }
 *     }
 * </pre>
 *
 * All the session configuration is optional: if you omit everything, the appropriate keys will be generated and the
 * cookie name used will be {@link JwtCookieSession#OPENIG_JWT_SESSION}.
 *
 * <p>
 * The {@literal keystore} attribute is an optional attribute that references a {@link KeyStore} heap object. It will
 * be used to obtain the required encryption keys. If omitted, the {@literal alias} and {@literal password}
 * attributes will also be ignored, and a unique key pair will be generated.
 * <p>
 * The {@literal alias} string attribute specifies the name of the private key to obtain from the KeyStore. It is
 * only required when a {@literal keystore} is specified.
 * <p>
 * The {@literal password} static expression attribute specifies the password to use when reading the
 * private key from the KeyStore. It is only required when a {@literal keystore} is specified.
 * <p>
 * The {@literal cookieName} optional string attribute specifies the name of the cookie used to store the encrypted JWT.
 * If not set, {@link JwtCookieSession#OPENIG_JWT_SESSION} is used.
 *
 * @since 3.1
 */
public class JwtSessionManager extends GenericHeapObject implements SessionManager {

    /**
     * The pair of keys for JWT payload encryption/decryption.
     */
    private final KeyPair keyPair;

    /**
     * The name of the cookie to be used to session's content transmission.
     */
    private final String cookieName;

    /**
     * Builds a new JwtSessionFactory using the given KeyPair for session encryption, storing the opaque result in a
     * cookie with the given name.
     *
     * @param keyPair
     *         Private and public keys used for ciphering/deciphering
     * @param cookieName
     *         name of the cookie
     */
    public JwtSessionManager(final KeyPair keyPair, final String cookieName) {
        this.keyPair = keyPair;
        this.cookieName = cookieName;
    }

    @Override
    public Session load(final Request request) {
        return new JwtCookieSession(request, keyPair, cookieName, logger);
    }

    @Override
    public void save(Session session, Response response) throws IOException {
        session.save(response);
    }

    /** Creates and initializes a jwt-session in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        /** RSA needs at least a 512 key length.*/
        private static final int KEY_SIZE = 1024;

        @Override
        public Object create() throws HeapException {
            KeyPair keyPair = null;
            JsonValue keystoreValue = config.get("keystore");
            if (!keystoreValue.isNull()) {
                KeyStore keyStore = heap.resolve(keystoreValue, KeyStore.class);

                String alias = config.get("alias").required().asString();
                String password = evaluate(config.get("password").required());

                try {
                    Key key = keyStore.getKey(alias, password.toCharArray());
                    if (key instanceof PrivateKey) {
                        // Get certificate of private key
                        Certificate cert = keyStore.getCertificate(alias);
                        if (cert == null) {
                            throw new HeapException(format("Cannot get Certificate[alias:%s] from KeyStore[ref:%s]",
                                                           alias,
                                                           keystoreValue.asString()));
                        }

                        // Get public key
                        PublicKey publicKey = cert.getPublicKey();

                        // Return a key pair
                        keyPair = new KeyPair(publicKey, (PrivateKey) key);
                    } else {
                        throw new HeapException(format("Either no Key[alias:%s] is available in KeyStore[ref:%s], "
                                                       + "or it is not a private key",
                                                       alias,
                                                       keystoreValue.asString()));
                    }
                } catch (GeneralSecurityException e) {
                    throw new HeapException(format("Wrong password for Key[alias:%s] in KeyStore[ref:%s]",
                                                   alias,
                                                   keystoreValue.asString()),
                                            e);
                }
            } else {
                // No KeyStore provided: generate a new KeyPair by our-self
                // In that case, 'alias' and 'password' attributes are ignored
                try {
                    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                    generator.initialize(KEY_SIZE, new SecureRandom());
                    keyPair = generator.generateKeyPair();
                } catch (NoSuchAlgorithmException e) {
                    throw new HeapException("Cannot build a random KeyPair", e);
                }
            }

            // Create the session factory with the given KeyPair and cookie name
            return new JwtSessionManager(keyPair,
                                         config.get("cookieName").defaultTo(OPENIG_JWT_SESSION).asString());
        }
    }
}
