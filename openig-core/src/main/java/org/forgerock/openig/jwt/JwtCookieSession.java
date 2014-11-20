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
import static org.forgerock.openig.util.Json.*;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.jose.builders.EncryptedJwtBuilder;
import org.forgerock.json.jose.builders.JwtBuilderFactory;
import org.forgerock.json.jose.builders.JwtClaimsSetBuilder;
import org.forgerock.json.jose.common.JwtReconstruction;
import org.forgerock.json.jose.exceptions.JweDecryptionException;
import org.forgerock.json.jose.jwe.EncryptedJwt;
import org.forgerock.json.jose.jwe.EncryptionMethod;
import org.forgerock.json.jose.jwe.JweAlgorithm;
import org.forgerock.json.jose.jwt.JwtClaimsSet;
import org.forgerock.openig.http.Cookie;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Session;
import org.forgerock.openig.jwt.dirty.DirtyCollection;
import org.forgerock.openig.jwt.dirty.DirtyListener;
import org.forgerock.openig.jwt.dirty.DirtySet;
import org.forgerock.openig.log.Logger;
import org.forgerock.util.MapDecorator;

/**
 * Represents an OpenIG {@link Session} that will be stored as an encrypted JSON Web Token in a Cookie.
 * The generated JWT is encrypted with the {@link JweAlgorithm#RSAES_PKCS1_V1_5} algorithm and {@link
 * EncryptionMethod#A128CBC_HS256} method.
 */
public class JwtCookieSession extends MapDecorator<String, Object> implements Session, DirtyListener {

    /**
     * Name of the cookie that will store the JWT session.
     */
    public static final String OPENIG_JWT_SESSION = "openig-jwt-session";

    /**
     * {@literal exchange.request} will be used to read existing cookie (if any), and {@literal exchange.response} will
     * be used to write the new cookie value.
     */
    private final Exchange exchange;

    /**
     * Know how to rebuild a JWT from a String.
     */
    private final JwtReconstruction reader = new JwtReconstruction();

    /**
     * Factory for JWT.
     */
    private final JwtBuilderFactory factory = new JwtBuilderFactory();

    /**
     * Marker used to detect if the session was used or not.
     */
    private boolean dirty = false;

    /**
     * Name to be used for the JWT Cookie.
     */
    private final String cookieName;

    /**
     * Logger used to output warnings about session's size.
     */
    private final Logger logger;

    /**
     * Used for decryption/encryption of session's content.
     */
    private final KeyPair pair;

    /**
     * Builds a new JwtCookieSession that will manage the given Exchange's session.
     *
     * @param exchange
     *         Exchange used to access {@literal Cookie} and {@literal Set-Cookie} headers.
     * @param pair
     *         Secret key used to sign the JWT payload.
     * @param cookieName
     *         Name to be used for the JWT Cookie.
     * @param logger
     *         Logger
     */
    public JwtCookieSession(final Exchange exchange,
                            final KeyPair pair,
                            final String cookieName,
                            final Logger logger) {
        super(new LinkedHashMap<String, Object>());
        this.exchange = exchange;
        this.pair = pair;
        this.cookieName = cookieName;
        this.logger = logger;

        // TODO Make this lazy (intercept read methods)
        loadJwtSession();
    }

    /**
     * Load the session's content from the cookie (if any).
     */
    private void loadJwtSession() {
        Cookie cookie = findJwtSessionCookie();
        if (cookie != null) {
            try {
                EncryptedJwt jwt = reader.reconstructJwt(cookie.getValue(), EncryptedJwt.class);
                jwt.decrypt(pair.getPrivate());
                JwtClaimsSet claimsSet = jwt.getClaimsSet();
                for (String key : claimsSet.keys()) {
                    // directly use super to avoid session be marked as dirty
                    super.put(key, claimsSet.getClaim(key));
                }
            } catch (JweDecryptionException e) {
                dirty = true; // Force cookie expiration / overwrite.
                logger.warning(format("The JWT Session Cookie '%s' could not be decrypted. This "
                        + "may be because temporary encryption keys have been used or if the "
                        + "configured encryption keys have changed since the JWT Session Cookie "
                        + "was created", cookieName));
                logger.debug(e);
            } catch (Exception e) {
                dirty = true; // Force cookie expiration / overwrite.
                logger.warning(format("Cannot rebuild JWT Session from Cookie '%s'", cookieName));
                logger.debug(e);
            }
        }
    }

    @Override
    public void onElementsRemoved() {
        dirty = true;
    }

    @Override
    public Object put(final String key, final Object value) {
        // Put null into a key, results in the complete entry removal
        if (value == null) {
            return remove(key);
        }
        // Verify that the given value is JSON compatible
        // This will throw an Exception if not
        checkJsonCompatibility(key, value);

        // Mark the session as dirty
        dirty = true;

        // Store the value
        return super.put(key, value);
    }

    @Override
    public void putAll(final Map<? extends String, ?> m) {
        for (Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Object remove(final Object key) {
        dirty = true;
        return super.remove(key);
    }

    @Override
    public void clear() {
        dirty = true;
        super.clear();
    }

    @Override
    public Set<String> keySet() {
        return new DirtySet<String>(super.keySet(), this);
    }

    @Override
    public Collection<Object> values() {
        return new DirtyCollection<Object>(super.values(), this);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return new DirtySet<Entry<String, Object>>(super.entrySet(), this);
    }

    @Override
    public void close() throws IOException {
        // Only build the JWT session if the session is dirty
        if (dirty) {
            // Update the Set-Cookie header
            final String value;
            if (isEmpty()) {
                value = buildExpiredJwtCookie();
            } else {
                value = buildJwtCookie();
                if (value.length() > 4096) {
                    throw new IOException(
                            format("JWT session is too large (%d chars), failing the request because "
                                    + "session does not support serialized content that is larger than 4KB "
                                    + "(Http Cookie limitation)", value.length()));
                }
                if (value.length() > 3072) {
                    logger.warning(format(
                            "Current JWT session's size (%d chars) is quite close to the 4KB limit. Maybe "
                                    + "consider using the traditional Http-based session (the default), or place"
                                    + "less objects in the session", value.length()));
                }
            }
            exchange.response.getHeaders().add("Set-Cookie", value);
        }

    }

    private String buildExpiredJwtCookie() {
        return format("%s=; Path=/; Max-Age=-1", cookieName);
    }

    private String buildJwtCookie() {
        return format("%s=%s; Path=%s", cookieName, buildJwtSession(), "/");
    }

    /**
     * Builds a JWT from the session's content.
     */
    private String buildJwtSession() {
        EncryptedJwtBuilder jwtBuilder = factory.jwe(pair.getPublic());
        JwtClaimsSetBuilder claimsBuilder = factory.claims();
        claimsBuilder.claims(this);
        jwtBuilder.claims(claimsBuilder.build());
        jwtBuilder.headers()
                  .alg(JweAlgorithm.RSAES_PKCS1_V1_5)
                  .enc(EncryptionMethod.A128CBC_HS256);
        return jwtBuilder.build();
    }

    /**
     * Find if there is an existing cookie storing a JWT session.
     *
     * @return a {@link Cookie} if found, {@literal null} otherwise.
     */
    private Cookie findJwtSessionCookie() {
        List<Cookie> cookies = exchange.request.getCookies().get(cookieName);
        if (cookies != null) {
            return cookies.get(0);
        }
        return null;
    }
}
