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
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.http.util;

import static java.nio.charset.Charset.*;

import java.nio.charset.Charset;

/**
 * Defines standards charsets used in OpenIG. <p>
 * <b>Note for Java 7 and later:</b>
 * this class will be removed and all references should be
 * updated to {@link java.nio.charset.StandardCharsets} instead.
 */
public final class StandardCharsets {

    /** UTF-8 Charset. */
    public static final Charset UTF_8 = forName("UTF-8");

    /** ISO-8859-1 Charset. */
    public static final Charset ISO_8859_1 = forName("ISO-8859-1");

    private StandardCharsets() { }
}
