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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static java.util.Collections.emptyList;
import static org.forgerock.json.JsonValueFunctions.charset;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.openig.util.JsonValues.evaluated;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Header;
import org.forgerock.http.protocol.Message;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.util.CaseInsensitiveSet;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.util.MessageType;
import org.forgerock.services.context.Context;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/**
 * Encrypts and decrypts header fields.
 * All cipher algorithms provided by SunJCE Provider are supported
 * for encryption but, for now CryptoHeaderFilter does
 * not implement a way to set/retrieve the initialization vector(IV) (OPENIG-42)
 * therefore, the CryptoHeader can not decrypt cipher algorithm using IV.
 */
public class CryptoHeaderFilter extends GenericHeapObject implements Filter {

    /**
     * Default cipher algorithm to be used when none is specified.
     */
    public static final String DEFAULT_ALGORITHM = "AES/ECB/PKCS5Padding";

    /** Should the filter encrypt or decrypt the given headers ? */
    public enum Operation {
        /**
         * Performs an encryption of the selected headers.
         */
        ENCRYPT,

        /**
         * Perform a decryption of the selected headers.
         * Notice that the decrypted value is a trimmed String using the given charset ({@code UTF-8} by default).
         */
        DECRYPT
    }

    /** Indicated the operation (encryption/decryption) to apply to the headers. */
    private Operation operation;

    /** Indicates the type of message to process headers for. */
    private MessageType messageType;

    /** Cryptographic algorithm. */
    private String algorithm;

    /** Encryption key. */
    private Key key;

    /** Indicates the {@link Charset} to use for decrypted values. */
    private Charset charset;

    /** The names of the headers whose values should be processed for encryption or decryption. */
    private final Set<String> headers = new CaseInsensitiveSet();

    /**
     * Sets the operation (encryption/decryption) to apply to the headers.
     *
     * @param operation
     *            The encryption/decryption) to apply to the headers.
     */
    public void setOperation(final Operation operation) {
        this.operation = operation;
    }

    /**
     * Sets the type of message to process headers for.
     *
     * @param messageType
     *            The type of message to process headers for.
     */
    public void setMessageType(final MessageType messageType) {
        this.messageType = messageType;
    }

    /**
     * Sets the cryptographic algorithm.
     *
     * @param algorithm
     *            The cryptographic algorithm.
     */
    public void setAlgorithm(final String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Sets the encryption key.
     *
     * @param key
     *            The encryption key to set.
     */
    public void setKey(final Key key) {
        this.key = key;
    }

    /**
     * The {@link Charset} to use for decrypted values.
     *
     * @param charset
     *            The charset used for decrypted values.
     */
    public void setCharset(final Charset charset) {
        this.charset = charset;
    }

    /**
     * Returns the headers whose values should be processed for encryption or decryption.
     *
     * @return The headers whose values should be processed for encryption or decryption.
     */
    public Set<String> getHeaders() {
        return headers;
    }

    /**
     * Finds headers marked for processing and encrypts or decrypts the values.
     *
     * @param message the message containing the headers to encrypt/decrypt.
     */
    private void process(Message message) {
        for (String s : this.headers) {
            Header header = message.getHeaders().get(s);
            if (header != null) {
                List<String> in = header.getValues();
                List<String> out = new ArrayList<>();
                message.getHeaders().remove(s);
                for (String value : in) {
                    out.add(operation == Operation.ENCRYPT ? encrypt(value) : decrypt(value));
                }
                message.getHeaders().put(s, out);
            }
        }
    }

    /**
     * Decrypts a string value.
     *
     * @param in the string to decrypt.
     * @return the decrypted value.
     */
    private String decrypt(String in) {
        String result = "";
        try {
            byte[] ciphertext = Base64.decode(in);
            Cipher cipher = Cipher.getInstance(this.algorithm);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] plaintext = cipher.doFinal(ciphertext);
            result = new String(plaintext, charset).trim();
        } catch (GeneralSecurityException gse) {
            logger.error(gse);
        }
        return result;
    }

    /**
     * Encrypts a string value.
     *
     * @param in the string to encrypt.
     * @return the encrypted value.
     */
    private String encrypt(String in) {
        String result = "";
        try {
            Cipher cipher = Cipher.getInstance(this.algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] ciphertext = cipher.doFinal(in.getBytes(Charset.defaultCharset()));
            result = Base64.encode(ciphertext).trim();
        } catch (GeneralSecurityException gse) {
            logger.error(gse);
        }
        return result;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        if (messageType == MessageType.REQUEST) {
            process(request);
        }

        Promise<Response, NeverThrowsException> promise = next.handle(context, request);

        // Hook a post-processing function only if needed
        if (messageType == MessageType.RESPONSE) {
            return promise.thenOnResult(new ResultHandler<Response>() {
                @Override
                public void handleResult(final Response response) {
                    process(response);
                }
            });
        }
        return promise;
    }

    /** Creates and initializes a header filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            CryptoHeaderFilter filter = new CryptoHeaderFilter();
            JsonValue evaluated = config.as(evaluated());
            filter.messageType = evaluated.get("messageType")
                                       .required()
                                       .as(enumConstant(MessageType.class));
            filter.operation = evaluated.get("operation").required().as(enumConstant(Operation.class));
            filter.algorithm = evaluated.get("algorithm").defaultTo(DEFAULT_ALGORITHM).asString();
            filter.charset = evaluated.get("charset").defaultTo("UTF-8").as(charset());
            byte[] key = Base64.decode(evaluated.get("key").required().asString());
            if ((key == null) || (key.length == 0)) {
                // We want to print the original expression, so we use config instead of evaluated
                throw new JsonValueException(config.get("key"),
                                             "key evaluation gave an empty result that is not allowed");
            }
            try {
                filter.key = new SecretKeySpec(key, evaluated.get("keyType").defaultTo("AES").asString());
            } catch (IllegalArgumentException iae) {
                throw new JsonValueException(config, iae);
            }
            filter.headers.addAll(evaluated.get("headers").defaultTo(emptyList()).asList(String.class));
            return filter;
        }
    }
}
