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
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.jwt.JwtCookieSession.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.jose.common.JwtReconstruction;
import org.forgerock.json.jose.jwe.EncryptedJwt;
import org.forgerock.json.jose.jwt.JwtClaimsSet;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class JwtCookieSessionTest {

    // @Checkstyle:off
    private static final BigInteger MODULUS = new BigInteger("8791241646273606363530743138466682857710006665746288923335499993647975314172150182688142208337957780959253668641058299308924577038067798942355217080985221");
    private static final BigInteger PRV_EXP = new BigInteger("5285047066871138702057581959998914169261891032908708627042668481159292909395626068592006158226313005725007395275807957072135194469637529567961288974510261");
    private static final BigInteger PUB_EXP = new BigInteger("65537");
    // @Checkstyle:on

    /**
     * Represents the following claims, signed using 'secret' with HMac 256.
     * {
     *     "a-value" : "ForgeRock OpenIG"
     * }
     */
    // @Checkstyle:off
    private static final String ORIGINAL = "eyAidHlwIjogIkpXVCIsICJhbGciOiAiUlNBRVNfUEtDUzFfVjFfNSIsICJlbmMiOiAiQTEyOENCQ19IUzI1NiIgfQ."
            + "VcBqC0hgiEdE2OqirUY9QGItTboPunTwlBaKOIQu81vwEYocaO20G0DecedPpiE99np5v1Rifw82kCfAd4Kvfg."
            + "InWrJbg39qUmMS11Hc54SA.xgatQtOnS-krnjq9hN_e3t4pPw_0yxJX1ByXOv0W0plRnAHoldtRFJLLOvS09TlC.14WSYyCegapzGCIU3fbGPw";
    // @Checkstyle:on

    /**
     * This claims represents a signed JWT token of the following payload.
     * {
     *     "a-value" : "ForgeRock OpenAM"
     * }
     * As we expect encrypted payload (instead of signed one), this value can be used as an invalid cookie value.
     */
    private static final String ALTERED = "eyAiYWxnIjogIkhTMjU2IiwgInR5cCI6ICJqd3QiIH0."
            + "eyAiYS12YWx1ZSI6ICJGb3JnZVJvY2sgT3BlbkFNIiB9."
            + "A8Z4xSPTfobTUYwwBaAymm1Ovfe1T3oMG5W9zFkOC-o";

    /**
     * Default logger.
     */
    private Logger logger = new Logger(new NullLogSink(), Name.of("Test"));

    /**
     * Static key pair used for test encryption/decryption.
     */
    private KeyPair keyPair;

    @BeforeMethod
    public void setUp() throws Exception {
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(MODULUS, PUB_EXP);
        RSAPrivateKeySpec privateKeySpec = new RSAPrivateKeySpec(MODULUS, PRV_EXP);

        KeyFactory factory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = factory.generatePrivate(privateKeySpec);
        PublicKey publicKey = factory.generatePublic(publicKeySpec);

        keyPair = new KeyPair(publicKey, privateKey);
    }

    @Test
    public void shouldStoreSessionContentInACookie() throws Exception {
        JwtCookieSession session = newJwtSession(new Request());
        session.put("a-value", "ForgeRock OpenIG");
        Response response = new Response();
        session.save(response);

        String cookie = response.getHeaders().getFirst("Set-Cookie");

        JwtClaimsSet claimsSet = decryptClaimsSet(cookie);
        assertThat(claimsSet.keys()).containsOnly("a-value");
        assertThat(claimsSet.get("a-value").asString()).isEqualTo("ForgeRock OpenIG");
    }

    private JwtClaimsSet decryptClaimsSet(final String cookieValue) {
        Pattern extract = Pattern.compile("openig-jwt-session=(.*); Path=/");
        Matcher matcher = extract.matcher(cookieValue);
        if (!matcher.matches()) {
            fail("JWT cannot be extracted");
        }
        String encrypted = matcher.group(1);
        EncryptedJwt jwt = new JwtReconstruction().reconstructJwt(encrypted, EncryptedJwt.class);
        jwt.decrypt(keyPair.getPrivate());
        return jwt.getClaimsSet();
    }

    @Test
    public void shouldDetectModifiedContentWithSessionClear() throws Exception {
        Request request = new Request();
        setRequestCookie(request, ORIGINAL);
        JwtCookieSession session = newJwtSession(request);
        session.clear();
        Response response = new Response();
        session.save(response);

        assertThat(response.getHeaders().get("Set-Cookie")).isNotNull();
    }

    @Test
    public void shouldDetectModifiedContentWithSessionRemove() throws Exception {
        Request request = new Request();
        setRequestCookie(request, ORIGINAL);
        JwtCookieSession session = newJwtSession(request);
        session.remove("a-value");
        Response response = new Response();
        session.save(response);

        assertThat(response.getHeaders().get("Set-Cookie")).isNotNull();
    }

    @Test
    public void shouldNotStoreSessionContentInACookieWhenSessionWasNotModified() throws Exception {
        JwtCookieSession session = newJwtSession(new Request());
        Response response = new Response();
        session.save(response);

        assertThat(response.getHeaders().get("Set-Cookie")).isNull();
    }

    @Test
    public void shouldNotStoreSessionContentInACookieWhenSessionWasNotModifiedButOnlyInitialized() throws Exception {
        Request request = new Request();
        setRequestCookie(request, ORIGINAL);
        JwtCookieSession session = newJwtSession(request);
        Response response = new Response();
        session.save(response);

        assertThat(response.getHeaders().get("Set-Cookie")).isNull();
    }

    @Test
    public void shouldNotLoadUnexpectedSignedJwt() throws Exception {
        Request request = new Request();
        setRequestCookie(request, ALTERED);

        JwtCookieSession session = newJwtSession(request);
        assertThat(session).isEmpty();
    }

    @Test
    public void shouldNotLoadInvalidJwt() throws Exception {
        Request request = new Request();
        setRequestCookie(request, "Completely-invalid-JWT");

        JwtCookieSession session = newJwtSession(request);
        assertThat(session).isEmpty();
    }

    @Test
    public void shouldLoadVerifiedJwtSession() throws Exception {
        Request request = new Request();
        setRequestCookie(request, ORIGINAL);

        JwtCookieSession session = newJwtSession(request);
        assertThat(session).containsOnly(entry("a-value", "ForgeRock OpenIG"));
    }

    @Test(expectedExceptions = IOException.class,
          expectedExceptionsMessageRegExp = "JWT session is too large.*")
    public void shouldFailIfSessionIsLargerThanFourThousandsKB() throws Exception {
        Request request = new Request();
        JwtCookieSession session = newJwtSession(request);
        session.put("more-than-4KB", generateMessageOf(5000));
        session.save(new Response());
    }

    @Test
    public void shouldWarnTheUserAboutGettingCloseToTheThreshold() throws Exception {
        Request request = new Request();
        Logger spied = spy(logger);
        JwtCookieSession session = new JwtCookieSession(request, keyPair, "Test", spied);
        session.put("in-between-3KB-and-4KB", generateMessageOf(2500));
        session.save(new Response());

        verify(spied).warning(matches("Current JWT session's size \\(.* chars\\) is quite close to the 4KB limit.*"));
    }

    private static Object generateMessageOf(final int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append("A");
        }
        return sb.toString();
    }

    private JwtCookieSession newJwtSession(final Request request)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        return new JwtCookieSession(request, keyPair, OPENIG_JWT_SESSION, logger);
    }

    private static void setRequestCookie(final Request request, final String cookie) {
        request.getHeaders().put("Cookie", format("%s=%s", OPENIG_JWT_SESSION, cookie));
    }
}
