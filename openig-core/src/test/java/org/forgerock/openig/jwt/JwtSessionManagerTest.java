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
 * Copyright 2015-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openig.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;

import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.json.jose.jws.handlers.HmacSigningHandler;
import org.forgerock.openig.heap.HeapUtilsTest;
import org.forgerock.openig.heap.Name;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class JwtSessionManagerTest {

    private static final HmacSigningHandler SIGNING_HANDLER =
            new HmacSigningHandler("HelloWorld".getBytes(StandardCharsets.UTF_8));

    private JwtSessionManager manager;

    @Mock
    private Session session;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        manager = new JwtSessionManager(null, null, null, null, null, SIGNING_HANDLER);
        session = mock(Session.class);
    }

    @Test
    public void shouldSaveSession() throws Exception {
        Response response = new Response(Status.OK);
        manager.save(session, response);
        verify(session).save(same(response));
    }

    @Test
    public void shouldNotSaveSession() throws Exception {
        manager.save(session, null);
        verifyNoMoreInteractions(session);
    }

    @Test
    public void shouldGenerateAtLeast2048BitKeyPairWhenNoKeystoreIsConfigured() throws Exception {
        JwtSessionManager created = (JwtSessionManager) new JwtSessionManager.Heaplet()
                .create(Name.of("this"), json(object()), HeapUtilsTest.buildDefaultHeap());

        RSAPublicKey publicKey = (RSAPublicKey) keyPairOf(created).getPublic();
        assertThat(publicKey.getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
    }

    private static KeyPair keyPairOf(JwtSessionManager manager) throws Exception {
        Field field = JwtSessionManager.class.getDeclaredField("keyPair");
        field.setAccessible(true);
        return (KeyPair) field.get(manager);
    }
}
