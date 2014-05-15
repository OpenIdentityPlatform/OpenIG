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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.filter;

// Java Standard Edition

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

// Apache Commons Codec
import org.apache.commons.codec.binary.Base64;

// JSON Fluent
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Message;
import org.forgerock.openig.http.MessageType;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.CaseInsensitiveSet;

/**
 * Encrypts and decrypts header fields.
 */
public class CryptoHeaderFilter extends GenericFilter {

    /** TODO: Description. */
    public enum Operation {
        ENCRYPT, DECRYPT
    }

    /** TODO: Description. */
    protected Operation operation;

    /** Indicates the type of message in the exchange to process headers for. */
    protected MessageType messageType;

    /** Cryptographic algorithm. */
    protected String algorithm;

    /** Encryption key. */
    protected Key key;

    /** TODO: Description. */
    protected Charset charset;

    /** The names of the headers whose values should be processed for encryption or decryption. */
    protected final CaseInsensitiveSet headers = new CaseInsensitiveSet();

    /**
     * Finds headers marked for processing and encrypts or decrypts the values.
     *
     * @param message the message containing the headers to encrypt/decrypt.
     */
    private void process(Message message) {
        for (String s : this.headers) {
            List<String> in = message.headers.get(s);
            if (in != null) {
                List<String> out = new ArrayList<String>();
                message.headers.remove(s);
                for (String value : in) {
                    out.add(operation == Operation.ENCRYPT ? encrypt(value) : decrypt(value));
                }
                message.headers.addAll(s, out);
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
        String result = null;
        try {
            byte[] ciphertext = new Base64(0).decode(in);
            Cipher cipher = Cipher.getInstance(this.algorithm);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] plaintext = cipher.doFinal(ciphertext);
            result = new String(plaintext, charset).trim();
        } catch (IllegalBlockSizeException ibse) {
// TODO: proper logging
            System.err.println(ibse);
        } catch (NoSuchPaddingException nspe) {
            System.err.println(nspe);
// TODO: proper logging
        } catch (NoSuchAlgorithmException nsae) {
// TODO: proper logging
            System.err.println(nsae);
        } catch (InvalidKeyException ike) {
// TODO: proper logging
            System.err.println(ike);
        } catch (BadPaddingException bpe) {
// TODO: proper logging
            System.err.println(bpe);
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
        String result = null;
        try {
            Cipher cipher = Cipher.getInstance(this.algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] ciphertext = cipher.doFinal(in.getBytes());
            result = new Base64(0).encodeToString(ciphertext).trim();
        } catch (IllegalBlockSizeException ibse) {
// TODO: proper logging
            System.err.println(ibse);
        } catch (NoSuchPaddingException nspe) {
            System.err.println(nspe);
// TODO: proper logging
        } catch (NoSuchAlgorithmException nsae) {
// TODO: proper logging
            System.err.println(nsae);
        } catch (InvalidKeyException ike) {
// TODO: proper logging
            System.err.println(ike);
        } catch (BadPaddingException bpe) {
// TODO: proper logging
            System.err.println(bpe);
        }
        return result;
    }

    /**
     * Filters the request and/or response of an exchange by removing headers from and adding
     * headers to a message.
     */
    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        if (messageType == MessageType.REQUEST) {
            process(exchange.request);
        }
        next.handle(exchange);
        if (messageType == MessageType.RESPONSE) {
            process(exchange.response);
        }
        timer.stop();
    }

    /** Creates and initializes a header filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            CryptoHeaderFilter filter = new CryptoHeaderFilter();
            filter.messageType = config.get("messageType").required().asEnum(MessageType.class);
            filter.operation = config.get("operation").required().asEnum(Operation.class);
            filter.algorithm = config.get("algorithm").defaultTo("DES/ECB/NoPadding").asString();
            filter.charset = config.get("charset").defaultTo("UTF-8").asCharset();
            byte[] key = new Base64(0).decode(config.get("key").required().asString());
            if (key.length == 0) {
                throw new JsonValueException(config.get("key"), "Empty key is not allowed");
            }
            try {
                filter.key = new SecretKeySpec(key, config.get("keyType").defaultTo("DES").asString());
            } catch (IllegalArgumentException iae) {
                throw new JsonValueException(config, iae);
            }
            filter.headers.addAll(config.get("headers").defaultTo(Collections.emptyList()).asList(String.class));
            return filter;
        }
    }
}
