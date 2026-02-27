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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.filter;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.HeapUtilsTest;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.ArgumentCaptor;
import org.openidentityplatform.openig.secrets.SystemAndEnvSecretStore;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.crypto.KeyGenerator;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JwtBuilderFilterTest {
    final static String SECRET_VALUE = "s3cr3t";

    Handler mockHandler;
    SystemAndEnvSecretStore mockSecretStore;

    HeapImpl heap;

    Request request;
    @BeforeMethod
    public void setup() {
        mockHandler = mock(Handler.class);
        mockSecretStore = mock(SystemAndEnvSecretStore.class);

        when(mockHandler.handle(any(Context.class), any(Request.class)))
                .thenReturn(newResponsePromise(new Response(Status.OK)));

        heap = HeapUtilsTest.buildDefaultHeap();
        heap.put("secretStore", mockSecretStore);

        request = new Request();
    }



    @Test
    public void testFilterInitialization() throws HeapException {

        JsonValue config = json(object(
                field("template",
                        object(field("sub", "${attributes.subject}"))),
                field("signature",
                        object(
                                field("secretId", "jwt.signing.key"),
                                field("algorithm", "HS256")
                        )),
                field("encryption",
                        object(
                                field("secretId", "jwt.encryption.key"),
                                field("method", "A256GCM"),
                                field("algorithm", "A256KW")
                        ))
        ));

        when(mockSecretStore.getSecret(eq("jwt.signing.key"))).thenReturn(SECRET_VALUE.getBytes());
        when(mockSecretStore.getSecret(eq("jwt.encryption.key"))).thenReturn(SECRET_VALUE.getBytes());

        JwtBuilderFilter filter = (JwtBuilderFilter) new JwtBuilderFilter
                .Heaplet().create(Name.of("this"), config, heap);

        assertThat(filter).isNotNull();
    }

    @Test(dataProvider = "signatureAlgorithms")
    public void testSignature(String algorithm, byte[] secret) throws HeapException {


        JsonValue config = json(object(
                field("template",
                        object()),
                field("signature",
                        object(
                                field("secretId", "jwt.signing.key"),
                                field("algorithm", algorithm)
                        ))
        ));

        when(mockSecretStore.getSecret(anyString())).thenReturn(secret);

        JwtBuilderFilter filter = (JwtBuilderFilter) new JwtBuilderFilter
                .Heaplet().create(Name.of("this"), config, heap);

        filter.filter(new RootContext(), request, mockHandler);

        ArgumentCaptor<JwtBuilderContext> contextCaptor = ArgumentCaptor.forClass(JwtBuilderContext.class);
        verify(mockHandler).handle(contextCaptor.capture(), eq(request));

        JwtBuilderContext jwtContext = contextCaptor.getValue();
        assertThat(jwtContext).isNotNull();
        assertThat(jwtContext.getValue()).startsWith("eyJ");
    }

    @Test(dataProvider = "encryptionAlgorithms")
    public void testEncryption(String algorithm, String method, byte[] encryptionKey) throws HeapException {
        JsonValue config = json(object(
                field("template",
                        object(field("sub", "${attributes.subject}"))),
                field("signature",
                        object(
                                field("secretId", "jwt.signing.key"),
                                field("algorithm", "HS256")
                        )),
                field("encryption",
                        object(
                                field("secretId", "jwt.encryption.key"),
                                field("method", method),
                                field("algorithm", algorithm)
                        ))
        ));


        when(mockSecretStore.getSecret(eq("jwt.signing.key"))).thenReturn(SECRET_VALUE.getBytes());
        when(mockSecretStore.getSecret(eq("jwt.encryption.key"))).thenReturn(encryptionKey);

        JwtBuilderFilter filter = (JwtBuilderFilter) new JwtBuilderFilter
                .Heaplet().create(Name.of("this"), config, heap);

        filter.filter(new RootContext(), request, mockHandler);

        ArgumentCaptor<JwtBuilderContext> contextCaptor = ArgumentCaptor.forClass(JwtBuilderContext.class);
        verify(mockHandler).handle(contextCaptor.capture(), eq(request));

        JwtBuilderContext jwtContext = contextCaptor.getValue();
        assertThat(jwtContext).isNotNull();
        assertThat(jwtContext.getValue()).startsWith("eyJ");

    }


    @Test
    public void testClaims() throws HeapException {

        JsonValue config = json(object(
                field("template",
                        object(field("sub", "${attributes.subject}"),
                                field("exp", "${now.plusSeconds(20).epochSeconds}")))
        ));

        AttributesContext attributesContext = new AttributesContext(new RootContext());
        attributesContext.getAttributes().put("subject", "user1");

        JwtBuilderFilter filter = (JwtBuilderFilter) new JwtBuilderFilter
                .Heaplet().create(Name.of("this"), config, heap);


        filter.filter(attributesContext, request, mockHandler);

        ArgumentCaptor<JwtBuilderContext> contextCaptor = ArgumentCaptor.forClass(JwtBuilderContext.class);
        verify(mockHandler).handle(contextCaptor.capture(), eq(request));

        JwtBuilderContext jwtContext = contextCaptor.getValue();
        assertThat(jwtContext).isNotNull();
        assertThat(jwtContext.getClaims()).containsKey("sub").containsKey("exp");
        assertThat(jwtContext.getClaims().get("sub")).isEqualTo("user1");
        assertThat(jwtContext.getClaims().get("exp")).isInstanceOf(Long.class);
    }

    @Test(expectedExceptions = HeapException.class)
    public void testSignatureError() throws HeapException {
        JsonValue config = json(object(
                field("template",
                        object(field("sub", "${attributes.subject}"))),
                field("signature",
                        object(
                                field("secretId", "jwt.signing.key"),
                                field("algorithm", "RS256")
                        ))));

        when(mockSecretStore.getSecret(eq("jwt.signing.key"))).thenReturn("bad_key".getBytes());
        new JwtBuilderFilter
                .Heaplet().create(Name.of("this"), config, heap);

    }


    @DataProvider(name = "signatureAlgorithms")
    public Object[][] signatureAlgorithms() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {

        byte[] hmacSecret = SECRET_VALUE.getBytes(StandardCharsets.UTF_8);

        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        KeyPair rsaKeyPair = rsaGen.generateKeyPair();
        byte[] rsaPrivateKeyBytes = rsaKeyPair.getPrivate().getEncoded();

        // EC key pair
        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ecKeyPair = ecGen.generateKeyPair();
        byte[] ecPrivateKeyBytes = ecKeyPair.getPrivate().getEncoded();

        return new Object[][] {
                {"HS256", hmacSecret},
                {"HS384", hmacSecret},
                {"HS512", hmacSecret},
                {"RS256", rsaPrivateKeyBytes},
                {"RS384", rsaPrivateKeyBytes},
                {"RS512", rsaPrivateKeyBytes},
                {"ES256", ecPrivateKeyBytes},
                {"ES384", ecPrivateKeyBytes},
                {"ES512", ecPrivateKeyBytes}
        };
    }

    @DataProvider(name = "encryptionAlgorithms")
    public Object[][] encryptionAlgorithms() throws NoSuchAlgorithmException {
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        KeyPair rsaKeyPair = rsaGen.generateKeyPair();
        byte[] rsaPublicKeyBytes = rsaKeyPair.getPublic().getEncoded();


        // AES keys
        KeyGenerator aesGen = KeyGenerator.getInstance("AES");
        aesGen.init(128);
        byte[] aes128Key = aesGen.generateKey().getEncoded();
        aesGen.init(256);
        byte[] aes256Key = aesGen.generateKey().getEncoded();

        return new Object[][]{
                {"RSA1_5", "A128CBC-HS256", rsaPublicKeyBytes},
                {"RSA-OAEP", "A192CBC-HS384", rsaPublicKeyBytes},
                {"RSA-OAEP-256", "A256CBC-HS512", rsaPublicKeyBytes},
                {"dir", "A128GCM", aes128Key},
                {"A128KW", "A192GCM", aes128Key},
                {"A192KW", "A256GCM", aes128Key},
                {"A256KW", "A256GCM", aes256Key}
        };
    }
}