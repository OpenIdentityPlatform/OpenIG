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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.decoration.helper;

import static java.lang.String.format;

import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a logger for decorators, according to the pattern:
 * {@literal <decoratorClassname>.<decoratorName>.<decoratedObjectName>}.
 */
public abstract class AbstractDecorator implements Decorator {

    private final String name;

    /**
     * Provides the decorator name.
     *
     * @param name
     *            The name of the decorator.
     */
    protected AbstractDecorator(final String name) {
        this.name = name;
    }

    /**
     * Builds a logger based on the pattern
     * {@literal <decoratorClassname>.<decoratorName>.<decoratedObjectName>}.
     *
     * @param context
     *            The decoration context.
     * @return A logger.
     */
    protected Logger getLogger(final Context context) {
        return LoggerFactory.getLogger(format("%s.%s.%s",
                                       this.getClass().getName(),
                                       name,
                                       context.getName().getLeaf()));
    }
}
