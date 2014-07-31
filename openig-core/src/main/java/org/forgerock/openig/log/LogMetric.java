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

package org.forgerock.openig.log;

/**
 * Log entry data that provides a measurement.
 */
public class LogMetric {

    /** The unit of measurement the metric is expressed in. */
    private final String units;

    /** The numeric value of the metric. */
    private final Number value;

    /**
     * Constructs a new metric with the specific value and units.
     *
     * @param value
     *            the numeric value of the metric.
     * @param units
     *            the unit of measurement that the metric is expressed in.
     */
    public LogMetric(final Number value, final String units) {
        this.value = value;
        this.units = units;
    }

    /**
     * Returns the unit of measurement the metric is expressed in.
     *
     * @return The unit of measurement the metric is expressed in.
     */
    public String getUnits() {
        return units;
    }

    /**
     * Returns the numeric value of the metric.
     *
     * @return The numeric value of the metric.
     */
    public Number getValue() {
        return value;
    }

    /**
     * Returns the metric in the form <em>value</em> SP <em>units</em>. For
     * example, if value is {@code 100} and units are {@code "ms"}, then the
     * returned value would be {@code "100 ms"}.
     *
     * @return the metric in the form <em>value</em> SP <em>units</em>.
     */
    @Override
    public String toString() {
        return getValue().toString() + ' ' + getUnits();
    }
}
