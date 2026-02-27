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

package org.forgerock.openig.el.plugins;

import org.forgerock.openig.el.ExpressionInstant;
import org.forgerock.openig.el.ExpressionPlugin;

import java.time.Instant;


/**
 * An ELContext node plugin that provides access to {@link ExpressionInstant} instance.
 */
public class ExpressionInstantPlugin implements ExpressionPlugin {
    @Override
    public Object getObject() {
        return new ExpressionInstant(Instant.now());
    }

    @Override
    public String getKey() {
        return "now";
    }
}
