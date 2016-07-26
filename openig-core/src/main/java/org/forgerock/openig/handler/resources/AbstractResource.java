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

package org.forgerock.openig.handler.resources;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Common base for all {@link Resource} implementations.
 */
abstract class AbstractResource implements Resource {
    private final String type;

    AbstractResource(final String type) {
        this.type = type;
    }

    @Override
    public boolean hasChangedSince(final long sinceTime) {
        // Compare in seconds
        return SECONDS.convert(getLastModified(), MILLISECONDS) > (SECONDS.convert(sinceTime, MILLISECONDS));
    }

    @Override
    public String getType() {
        return type;
    }
}
