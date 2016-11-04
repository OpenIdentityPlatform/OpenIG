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

package org.forgerock.openig.openam;

import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;

/**
 * A {@link StsContext} convey the token transformation results to downstream filters and handlers.
 */
public class StsContext extends AbstractContext {

    private final String issuedToken;

    StsContext(final Context parent, final String token) {
        super(parent, "sts");
        issuedToken = checkNotNull(token);
    }

    /**
     * Returns the token issued by the OpenAM STS.
     * @return the token issued by the OpenAM STS.
     */
    public String getIssuedToken() {
        return issuedToken;
    }
}
