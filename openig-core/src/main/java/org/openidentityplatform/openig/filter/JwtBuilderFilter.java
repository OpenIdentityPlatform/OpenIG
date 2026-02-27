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

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.jose.builders.JwtBuilderFactory;
import org.forgerock.json.jose.builders.JwtClaimsSetBuilder;
import org.forgerock.json.jose.builders.SignedJwtBuilderImpl;
import org.forgerock.json.jose.builders.SignedThenEncryptedJwtBuilder;
import org.forgerock.json.jose.jwe.EncryptionMethod;
import org.forgerock.json.jose.jwe.JweAlgorithm;
import org.forgerock.json.jose.jws.JwsAlgorithm;
import org.forgerock.json.jose.jws.SigningManager;
import org.forgerock.json.jose.jws.handlers.SigningHandler;
import org.forgerock.json.jose.jwt.Jwt;
import org.forgerock.json.jose.jwt.JwtClaimsSet;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.util.JsonValues;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.openidentityplatform.openig.secrets.SystemAndEnvSecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;

import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.openig.el.Bindings.bindings;


/**
 * Filter that builds JWTs from configurable claims templates.
 * <p>
 * Supports signing with HMAC, RSA, and ECDSA algorithms, and optional
 * encryption with RSA or AES key wrapping.
 *
 * <p>Configuration example:
 * <pre>{@code
 * {
 *   "type": "JwtBuilderFilter",
 *   "config": {
 *      "template": {
 *          "sub": "${request.headers['X-User-ID'][0]}",
 *          "iss": "my-issuer"
 *      },
 *      "signature": {
 *          "algorithm": "RS256",
 *          "secretId": "jwt.signing.key"
 *      },
 *     "encryption": {
 *          "algorithm": "RSA-OAEP",
 *           "method": "A128GCM",
 *          "secretId": "jwt.encryption.key"
 *      }
 *   }
 * }
 * }</pre>
 *
 * @see JwtBuilderContext
 */
public class JwtBuilderFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(JwtBuilderFilter.class);

    private Function<Bindings, Map<String, Object>, ExpressionException> template;

    private Signature signature;

    private Encryption encryption;

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        final Bindings bindings = bindings(context, request);
        Map<String, Object> claims;
        try {
            claims = template.apply(bindings);
        } catch (ExpressionException e) {
            logger.error("Failed to evaluate claims template", e);
            return newResponsePromise(new Response(Status.INTERNAL_SERVER_ERROR));
        }

        JwtClaimsSetBuilder jwtClaimsSetBuilder = new JwtClaimsSetBuilder();
        JwtClaimsSet jwtClaims = jwtClaimsSetBuilder.claims(claims).build();

        JwtBuilderFactory jwtBuilderFactory = new JwtBuilderFactory();

        SignedJwtBuilderImpl jwtBuilder;
        if(this.signature != null) {
             jwtBuilder = jwtBuilderFactory.jws(this.signature.signingHandler).headers().alg(this.signature.algorithm).done();
        } else {
            jwtBuilder = jwtBuilderFactory.jwt().headers().done();
        }

        jwtBuilder = jwtBuilder.claims(jwtClaims);

        Jwt jwt;
        if (this.encryption != null) {
            SignedThenEncryptedJwtBuilder signedThenEncryptedJwtBuilder
                    = jwtBuilder.encrypt(this.encryption.key).headers()
                    .enc(this.encryption.method).alg(this.encryption.algorithm).done();
            jwt = signedThenEncryptedJwtBuilder.asJwt();

        } else {
            jwt = jwtBuilder.asJwt();
        }
        return next.handle(new JwtBuilderContext(context, jwt.build(), claims), request);
    }

    static class Signature {

        private final JwsAlgorithm algorithm;
        private final byte[] secret;

        private final SigningHandler signingHandler;


        Signature(JsonValue config, Heap heap) throws HeapException {
            this.algorithm = config.get("algorithm").defaultTo("RS256").as(enumConstant(JwsAlgorithm.class));
            String secretId = config.get("secretId").required().asString();
            this.secret = SystemAndEnvSecretStore.getSecretFromHeap(heap, secretId);
            if (this.secret == null || this.secret.length == 0) {
                throw new HeapException("Secret cannot be null or empty");
            }

            try {
                this.signingHandler = initSigningHandler();
            } catch (Exception e) {
                throw new HeapException("Error initializing signing handler", e);
            }
        }

        private SigningHandler initSigningHandler() throws NoSuchAlgorithmException, InvalidKeySpecException {
            SigningHandler signingHandler;
            switch (this.algorithm.getAlgorithmType()) {
                case HMAC: {
                    signingHandler = new SigningManager().newHmacSigningHandler(this.secret);
                    break;
                }
                case RSA: {
                    KeyFactory rsaFact = KeyFactory.getInstance("RSA");
                    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(this.secret);
                    RSAPrivateKey key = (RSAPrivateKey) rsaFact.generatePrivate(spec);
                    signingHandler = new SigningManager().newRsaSigningHandler(key);
                    break;
                }
                case ECDSA: {
                    KeyFactory ecFact = KeyFactory.getInstance("EC");
                    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(this.secret);
                    ECPrivateKey key = (ECPrivateKey) ecFact.generatePrivate(spec);
                    signingHandler = new SigningManager().newEcdsaSigningHandler(key);
                    break;
                }
                default:
                    signingHandler = null;
                    break;
            }
            return signingHandler;
        }
    }

    static class Encryption {

        private final byte[] secret;
        private final JweAlgorithm algorithm;
        private final EncryptionMethod method;

        private final Key key;

        Encryption(JsonValue config, Heap heap) throws HeapException {
            String secretId = config.get("secretId").required().asString();
            this.secret = SystemAndEnvSecretStore.getSecretFromHeap(heap, secretId);

            if (this.secret == null || this.secret.length == 0) {
                throw new HeapException("Secret cannot be null or empty");
            }

            this.algorithm = config.get("algorithm").required().as(a -> JweAlgorithm.parseAlgorithm(a.asString()));
            this.method = config.get("method").required().as(a -> EncryptionMethod.parseMethod(a.asString()));
            try {
                this.key = initEncryptionKey();
            }catch (Exception e) {
                throw new HeapException("Error initializing encryption key", e);
            }
        }

        private Key initEncryptionKey() throws NoSuchAlgorithmException, InvalidKeySpecException {

            switch (algorithm) {
                case RSA_OAEP:
                case RSA_OAEP_256:
                case RSAES_PKCS1_V1_5:
                    KeyFactory rsaFact = KeyFactory.getInstance("RSA");
                    X509EncodedKeySpec spec = new X509EncodedKeySpec(secret);
                    return rsaFact.generatePublic(spec);
                case DIRECT:
                case A128KW:
                case A192KW:
                case A256KW:
                    return new SecretKeySpec(secret, "AES");
                default:
                    throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
            }
        }
    }

    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            JwtBuilderFilter filter = new JwtBuilderFilter();

            filter.template = JsonValues.asFunction(config.get("template").required(), Object.class, heap.getProperties());

            if(config.isDefined("signature")) {
                filter.signature = new Signature(config.get("signature"), heap);
            }
            if(config.isDefined("encryption")) {
                filter.encryption = new Encryption(config.get("encryption"), heap);
            }
            return filter;
        }
    }
}
