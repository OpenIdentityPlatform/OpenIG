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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * ISO8601 compliant representation of dates and times
 */
public final class ISO8601 {

    /** Format to output dates in. Must be used in synchronized block. */
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    static {
        SDF.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Private constructor for utility class.
     */
    private ISO8601() { }

    /**
     * Returns an ISO8601 compliant string representing a date from a Java Date object.
     *
     * @param date The date.
     * @return The IS08601 compliant string representation of the date.
     */
    public static String format(Date date) {
        synchronized (SDF) {
            return SDF.format(date);
        }
    }

    /**
     * Returns an ISO8601 compliant string representing a date from a long
     *
     * @param date the date input as a long.
     * @return The IS08601 compliant string representation of the date.
     */
    public static String format(long date) {
        return format(new Date(date));
    }
}
