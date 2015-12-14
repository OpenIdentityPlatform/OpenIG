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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.jwt;

import static java.lang.String.*;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.jwt.JwtCookieSession.*;
import static org.forgerock.openig.util.JsonValues.*;
import static org.forgerock.util.time.Duration.duration;

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

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionManager;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/**
 * A JwtSessionManager is responsible to configure and create a {@link JwtCookieSession}.
 *
 * <pre>
 *     {@code
 *     {
 *         "name": "JwtSession",
 *         "type": "JwtSession",
 *         "config": {
 *             "keystore": "Ref To A KeyStore",
 *             "alias": "PrivateKey Alias",
 *             "password": "KeyStore/Key Password",
 *             "cookieName": "OpenIG",
 *             "sessionTimeout": "30 minutes"
 *         }
 *     }
 *     }
 * </pre>
 *
 * All the session configuration is optional: if you omit everything, the appropriate keys will be generated and the
 * cookie name used will be {@link JwtCookieSession#OPENIG_JWT_SESSION}.
 *
 * <p>
 * The {@literal keystore} attribute is an optional attribute that references a {@link KeyStore} heap object. It will
 * be used to obtain the required encryption keys. If omitted, the {@literal alias} and {@literal password}
 * attributes will also be ignored, and a temporary key pair will be generated.
 * <p>
 * The {@literal alias} string attribute specifies the name of the private key to obtain from the KeyStore. It is
 * only required when a {@literal keystore} is specified.
 * <p>
 * The {@literal password} static expression attribute specifies the password to use when reading the
 * private key from the KeyStore. It is only required when a {@literal keystore} is specified.
 * <p>
 * The {@literal cookieName} optional string attribute specifies the name of the cookie used to store the encrypted JWT.
 * If not set, {@link JwtCookieSession#OPENIG_JWT_SESSION} is used.
 * <p>
 * The {@literal sessionTimeout} optional duration attribute, specifies the amount of time before the cookie session
 * expires. If not set, a default of 30 minutes is used. A duration of 0 is not valid and it will be limited to
 * a maximum duration of approximately 10 years.
 *
 * @since 3.1
 */
public class JwtSessionManager extends GenericHeapObject implements SessionManager {

    /**
     * Default sessionTimeout duration.
     */
    public static final String DEFAULT_SESSION_TIMEOUT = "30 minutes";

    /**
     * The maximum session timeout duration, allows for an expiry time of approx 10 years (does not take leap years
     * into consideration).
     */
    public static final Duration MAX_SESSION_TIMEOUT = Duration.duration("3650 days");

    /**
     * The pair of keys for JWT payload encryption/decryption.
     */
    private final KeyPair keyPair;

    /**
     * The name of the cookie to be used to session's content transmission.
     */
    private final String cookieName;

    /**
     * The TimeService to use when setting the cookie session expiry time.
     */
    private final TimeService timeService;

    /**
     * How long before the cookie session expires.
     */
    private final Duration sessionTimeout;

    /**
     * Builds a new JwtSessionManager using the given KeyPair for session encryption, storing the opaque result in a
     * cookie with the given name.
     *
     * @param keyPair
     *         Private and public keys used for ciphering/deciphering
     * @param cookieName
     *         name of the cookie
     * @param timeService
     *         TimeService to use when dealing with cookie sessions
     * @param sessionTimeout
     *         The duration of the cookie session
     */
    public JwtSessionManager(final KeyPair keyPair,
                             final String cookieName,
                             final TimeService timeService,
                             final Duration sessionTimeout) {
        this.keyPair = keyPair;
        this.cookieName = cookieName;
        this.timeService = timeService;
        this.sessionTimeout = sessionTimeout;
    }

    @Override
    public Session load(final Request request) {
        return new JwtCookieSession(request, keyPair, cookieName, logger, timeService, sessionTimeout);
    }

    @Override
    public void save(Session session, Response response) throws IOException {
        if (response != null) {
            session.save(response);
        }
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
                /*
                 * No KeyStore provided: generate a new KeyPair by ourself. In
                 * this case, 'alias' and 'password' attributes are ignored. JWT
                 * session cookies will not be portable between OpenIG instances
                 * config changes, and restarts.
                 */
                try {
                    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                    generator.initialize(KEY_SIZE, new SecureRandom());
                    keyPair = generator.generateKeyPair();
                } catch (NoSuchAlgorithmException e) {
                    throw new HeapException("Cannot build a random KeyPair", e);
                }

                logger.warning("JWT session support has been enabled but no encryption keys have "
                        + "been configured. A temporary key pair will be used but this means that "
                        + "OpenIG will not be able to decrypt any JWT session cookies after a "
                        + "configuration change, a server restart, nor will it be able to decrypt "
                        + "JWT session cookies encrypted by another OpenIG server.");
            }

            TimeService timeService = heap.get(TIME_SERVICE_HEAP_KEY, TimeService.class);

            final Duration sessionTimeout =
                    duration(config.get("sessionTimeout").defaultTo(DEFAULT_SESSION_TIMEOUT).asString());
            if (sessionTimeout.isZero()) {
                throw new HeapException("sessionTimeout duration must be greater than 0");
            }

            // Create the session factory with the given KeyPair and cookie name
            return new JwtSessionManager(keyPair,
                                         config.get("cookieName").defaultTo(OPENIG_JWT_SESSION).asString(),
                                         timeService,
                                         sessionTimeout);
        }
    }
}
