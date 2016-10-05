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
package org.forgerock.openig.util;

import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.CrestApplication;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Resources;

/**
 * CREST utility class.
 */
public final class CrestUtil {

    private CrestUtil() { }

    /**
     * Creates a new {@link CrestApplication}.
     *
     * @param requestHandler
     *         The {@link RequestHandler} used for the {@link ConnectionFactory}.
     * @param apiId
     *         The API ID string to build the {@link CrestApplication}.
     *         Should start with mandatory 'frapi', i.e:
     *         'frapi:openig:service.'
     * @return a new CrestApplication.
     *
     */
    public static CrestApplication newCrestApplication(final RequestHandler requestHandler, final String apiId) {
        final ConnectionFactory connectionFactory = Resources.newInternalConnectionFactory(requestHandler);
        return new CrestApplication() {

            @Override
            public ConnectionFactory getConnectionFactory() {
                return connectionFactory;
            }

            @Override
            public String getApiId() {
                return apiId;
            }

            @Override
            public String getApiVersion() {
                return VersionUtil.getVersion();
            }
        };
    }
}
