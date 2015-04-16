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

package org.forgerock.openig.filter;

import static java.security.KeyPairGenerator.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.http.util.StandardCharsets.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.filter.CryptoHeaderFilter.*;
import static org.forgerock.openig.filter.CryptoHeaderFilter.Operation.*;
import static org.forgerock.openig.log.LogSink.*;
import static org.forgerock.openig.util.MessageType.*;
import static org.forgerock.util.encode.Base64.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CryptoHeaderFilterTest {

    public static final String HEADER_NAME = "Free-Text";
    public static final String CLEAR_TEXT_VALUE = "Clear text value";
    public static final String ENCRYPTED_VALUE = "uPIQxqe5mbcnMw/SlMl0tvRRYGjkU77b/7tz3twFxRc=";
    /** The secret key length should be 16 with AES */
    public static final String B64_ENCODED_KEY = "VGhpc0lzQVNlY3JldEtleQ==";

    /** Cipher algorithms required in a standard JVM. */
    @DataProvider
    private Object[][] algorithms() {
        return new Object[][] {
            { "AES/CBC/NoPadding" },
            { "AES/CBC/PKCS5Padding" },
            { "AES/ECB/NoPadding" },
            { "AES/ECB/PKCS5Padding" },
            { "DES/CBC/NoPadding" },
            { "DES/CBC/PKCS5Padding" },
            { "DES/ECB/NoPadding" },
            { "DES/ECB/PKCS5Padding" },
            { "DESede/CBC/NoPadding" },
            { "DESede/CBC/PKCS5Padding" },
            { "DESede/ECB/NoPadding" },
            { "DESede/ECB/PKCS5Padding" },
            { "RSA/ECB/PKCS1Padding" },
            { "RSA/ECB/OAEPWithSHA-1AndMGF1Padding" },
            { "RSA/ECB/OAEPWithSHA-256AndMGF1Padding" } };
    }

    @Mock
    private Handler terminalHandler;

    @Spy
    private Logger logger = new Logger(new NullLogSink(), Name.of("source"));

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldEncryptionUsingDefaultAlgorithmWithInvalidKeyReturnsNull() throws Exception {
        CryptoHeaderFilter filter = buildDefaultCryptoHeader();
        filter.setMessageType(REQUEST);
        filter.setKey(new SecretKeySpec(decode("zuul"), "AES"));

        Request request = new Request();
        request.getHeaders().putSingle(HEADER_NAME, CLEAR_TEXT_VALUE);

        when(terminalHandler.handle(null, request))
                .thenReturn(Promises.<Response, ResponseException>newSuccessfulPromise(new Response()));

        filter.filter(null, request, terminalHandler);

        verify(logger).error(any(GeneralSecurityException.class));
        assertThat(request.getHeaders().getFirst(HEADER_NAME)).isNull();
    }

    @Test
    public void shouldEncryptionWithInvalidAlgorithmReturnsNull() throws Exception {
        CryptoHeaderFilter filter = buildDefaultCryptoHeader();
        filter.setMessageType(REQUEST);
        filter.setAlgorithm("Unknown");

        Request request = new Request();
        request.getHeaders().putSingle(HEADER_NAME, CLEAR_TEXT_VALUE);

        when(terminalHandler.handle(null, request))
                .thenReturn(Promises.<Response, ResponseException>newSuccessfulPromise(new Response()));

        filter.filter(null, request, terminalHandler);

        verify(logger).error(any(GeneralSecurityException.class));
        assertThat(request.getHeaders().getFirst(HEADER_NAME)).isNull();
    }

    @Test
    public void testRequestEncryptionUsingDefaultAlgorithm() throws Exception {
        CryptoHeaderFilter filter = buildDefaultCryptoHeader();
        filter.setMessageType(REQUEST);

        Request request = new Request();
        request.getHeaders().putSingle(HEADER_NAME, CLEAR_TEXT_VALUE);

        when(terminalHandler.handle(null, request))
                .thenReturn(Promises.<Response, ResponseException>newSuccessfulPromise(new Response()));

        filter.filter(null, request, terminalHandler);

        verifyZeroInteractions(logger);
        assertThat(request.getHeaders().getFirst(HEADER_NAME))
                .isEqualTo(ENCRYPTED_VALUE);
    }

    @Test
    public void testRequestDecryptionUsingDefaultAlgorithm() throws Exception {
        CryptoHeaderFilter filter = buildDefaultCryptoHeader();
        filter.setMessageType(REQUEST);
        filter.setOperation(DECRYPT);

        Request request = new Request();
        request.getHeaders().putSingle(HEADER_NAME, ENCRYPTED_VALUE);

        when(terminalHandler.handle(null, request))
                .thenReturn(Promises.<Response, ResponseException>newSuccessfulPromise(new Response()));

        filter.filter(null, request, terminalHandler);

        verifyZeroInteractions(logger);
        assertThat(request.getHeaders().getFirst(HEADER_NAME))
                .isEqualTo(CLEAR_TEXT_VALUE);
    }

    @Test
    public void testResponseEncryptionUsingDefaultAlgorithm() throws Exception {
        CryptoHeaderFilter filter = buildDefaultCryptoHeader();
        filter.setMessageType(RESPONSE);

        Response response = new Response();
        response.getHeaders().putSingle(HEADER_NAME, CLEAR_TEXT_VALUE);

        when(terminalHandler.handle(null, null))
                .thenReturn(Promises.<Response, ResponseException>newSuccessfulPromise(response));

        filter.filter(null, null, terminalHandler);

        verifyZeroInteractions(logger);
        assertThat(response.getHeaders().getFirst(HEADER_NAME))
                .isEqualTo(ENCRYPTED_VALUE);
    }

    @Test
    public void testResponseDecryptionUsingDefaultAlgorithm() throws Exception {
        CryptoHeaderFilter filter = buildDefaultCryptoHeader();
        filter.setMessageType(RESPONSE);
        filter.setOperation(CryptoHeaderFilter.Operation.DECRYPT);

        Response response = new Response();
        response.getHeaders().putSingle(HEADER_NAME, ENCRYPTED_VALUE);

        when(terminalHandler.handle(null, null))
                .thenReturn(Promises.<Response, ResponseException>newSuccessfulPromise(response));

        filter.filter(null, null, terminalHandler);

        verifyZeroInteractions(logger);
        assertThat(response.getHeaders().getFirst(HEADER_NAME))
                .isEqualTo(CLEAR_TEXT_VALUE);
    }

    @Test(dataProvider = "algorithms")
    public void testEncryptionAlgorithms(final String algorithm) throws Exception {
        CryptoHeaderFilter filter = new CryptoHeaderFilter();
        filter.setMessageType(REQUEST);
        filter.setOperation(ENCRYPT);
        filter.setAlgorithm(algorithm);

        filter.setKey(buildKey(algorithm));
        filter.getHeaders().add(HEADER_NAME);
        filter.logger = logger;

        Request request = new Request();
        request.getHeaders().putSingle(HEADER_NAME, CLEAR_TEXT_VALUE);

        filter.filter(null, request, terminalHandler);

        verifyZeroInteractions(logger);
        assertThat(request.getHeaders().getFirst(HEADER_NAME))
                .isNotNull()
                .isNotEmpty()
                .isNotEqualTo(CLEAR_TEXT_VALUE);
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void testHeapletWithWrongKeyConfigurationFailsProperly() throws Exception {
        CryptoHeaderFilter.Heaplet heaplet = new CryptoHeaderFilter.Heaplet();
        JsonValue config = json(object(field("messageType", "REQUEST"),
                                       field("operation", "DECRYPT"),
                                       field("key", "DESKEY"))); // Not a valid key format

        // Note: I've used the special name LogSink to avoid having to configure a real heap
        heaplet.create(Name.of(LOGSINK_HEAP_KEY), config, null);
    }

    private CryptoHeaderFilter buildDefaultCryptoHeader() {
        final CryptoHeaderFilter filter = new CryptoHeaderFilter();
        filter.setOperation(ENCRYPT);
        filter.setAlgorithm(DEFAULT_ALGORITHM);
        filter.getHeaders().add(HEADER_NAME);
        filter.setKey(buildKey(DEFAULT_ALGORITHM));
        filter.setCharset(UTF_8);
        filter.logger = logger;
        return filter;
    }

    /** Generates a key according to the algorithm in use. */
    private static Key buildKey(final String algorithm) {
        if (algorithm.contains("DESede")) {
            SecretKeyFactory factory;
            try {
                factory = SecretKeyFactory.getInstance("DESede");
                // Key length should be at least 24 bytes.
                return factory.generateSecret(new DESedeKeySpec(
                        decode("U2VjcmV0S2V5Q29udGFpbmluZzI0Q2hh")));
            } catch (Exception e) {
                return null;
            }
        } else if (algorithm.contains("DES")) {
            // Key length should be 8 bytes.
            return new SecretKeySpec(decode("MTIzNDU2Nzg="), "DES");
        } else if (algorithm.contains("RSA")) {
            KeyPairGenerator kpGenerator;
            try {
                kpGenerator = getInstance("RSA");
            } catch (NoSuchAlgorithmException e) {
                return null;
            }
            // 1024, 2048 supported.
            kpGenerator.initialize(1024);
            return kpGenerator.genKeyPair().getPublic();
        } else {
            // Key length should be 16 bytes.
            return new SecretKeySpec(decode(B64_ENCODED_KEY), "AES");
        }
    }
}
