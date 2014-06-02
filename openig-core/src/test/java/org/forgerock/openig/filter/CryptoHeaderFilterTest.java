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

package org.forgerock.openig.filter;

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.MessageType;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.util.encode.Base64;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

public class CryptoHeaderFilterTest {

    public static final String HEADER_NAME = "Free-Text";
    public static final String CLEAR_TEXT_VALUE = "Clear text value";
    public static final String ENCRYPTED_VALUE = "pGuPljcDYhrRRvh8PZ7HXw==";
    public static final String B64_ENCODED_KEY = "MTIzNDU2Nzg=";

    @Mock
    private Handler terminalHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testRequestEncryption() throws Exception {
        CryptoHeaderFilter filter = new CryptoHeaderFilter();
        filter.setMessageType(MessageType.REQUEST);
        filter.setOperation(CryptoHeaderFilter.Operation.ENCRYPT);
        filter.setAlgorithm(CryptoHeaderFilter.DEFAULT_ALGORITHM);
        filter.getHeaders().add(HEADER_NAME);
        filter.setKey(buildKey());

        Exchange exchange = new Exchange();
        exchange.request = new Request();

        // Value length has to be a multiple of 8 because of the DES encryption mechanism
        exchange.request.headers.putSingle(HEADER_NAME, CLEAR_TEXT_VALUE);

        filter.filter(exchange, terminalHandler);

        assertThat(exchange.request.headers.getFirst(HEADER_NAME))
                .isEqualTo(ENCRYPTED_VALUE);
    }

    @Test
    public void testRequestDecryption() throws Exception {
        CryptoHeaderFilter filter = new CryptoHeaderFilter();
        filter.setMessageType(MessageType.REQUEST);
        filter.setOperation(CryptoHeaderFilter.Operation.DECRYPT);
        filter.setAlgorithm(CryptoHeaderFilter.DEFAULT_ALGORITHM);
        filter.getHeaders().add(HEADER_NAME);
        filter.setKey(buildKey());
        filter.setCharset(Charset.forName("UTF-8"));

        Exchange exchange = new Exchange();
        exchange.request = new Request();

        exchange.request.headers.putSingle(HEADER_NAME, ENCRYPTED_VALUE);

        filter.filter(exchange, terminalHandler);

        assertThat(exchange.request.headers.getFirst(HEADER_NAME))
                .isEqualTo(CLEAR_TEXT_VALUE);
    }

    @Test
    public void testResponseEncryption() throws Exception {
        CryptoHeaderFilter filter = new CryptoHeaderFilter();
        filter.setMessageType(MessageType.RESPONSE);
        filter.setOperation(CryptoHeaderFilter.Operation.ENCRYPT);
        filter.setAlgorithm(CryptoHeaderFilter.DEFAULT_ALGORITHM);
        filter.getHeaders().add(HEADER_NAME);
        filter.setKey(buildKey());

        final Exchange exchange = new Exchange();
        exchange.response = new Response();

        // Value length has to be a multiple of 8 because of the DES encryption mechanism
        exchange.response.headers.putSingle(HEADER_NAME, CLEAR_TEXT_VALUE);

        filter.filter(exchange, terminalHandler);

        assertThat(exchange.response.headers.getFirst(HEADER_NAME))
                .isEqualTo(ENCRYPTED_VALUE);
    }

    @Test
    public void testResponseDecryption() throws Exception {
        CryptoHeaderFilter filter = new CryptoHeaderFilter();
        filter.setMessageType(MessageType.RESPONSE);
        filter.setOperation(CryptoHeaderFilter.Operation.DECRYPT);
        filter.setAlgorithm(CryptoHeaderFilter.DEFAULT_ALGORITHM);
        filter.getHeaders().add(HEADER_NAME);
        filter.setKey(buildKey());
        filter.setCharset(Charset.forName("UTF-8"));

        final Exchange exchange = new Exchange();
        exchange.response = new Response();

        exchange.response.headers.putSingle(HEADER_NAME, ENCRYPTED_VALUE);

        filter.filter(exchange, terminalHandler);

        assertThat(exchange.response.headers.getFirst(HEADER_NAME))
                .isEqualTo(CLEAR_TEXT_VALUE);
    }

    /**
     * DES Key needs to have an 8 bytes length (decoded value)
     * 12345678 -> MTIzNDU2Nzg=
     */
    private static SecretKeySpec buildKey() {
        return new SecretKeySpec(Base64.decode(B64_ENCODED_KEY), "DES");
    }

}
