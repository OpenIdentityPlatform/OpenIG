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

package org.slf4j.impl;

import org.forgerock.openig.log.LogSinkLoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

/**
 * Static binding class used by SLF4J to discover about our specific implementation.
 */
public class StaticLoggerBinder implements LoggerFactoryBinder {

    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder(new LogSinkLoggerFactory());

    /**
     * Returns the binder's singleton.
     *
     * @return the binder's singleton.
     */
    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    private final ILoggerFactory loggerFactory;

    StaticLoggerBinder(final ILoggerFactory factory) {
        loggerFactory = factory;
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return LogSinkLoggerFactory.class.getName();
    }
}
