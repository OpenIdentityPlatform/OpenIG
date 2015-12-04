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

package org.forgerock.openig.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.routing.Router;
import org.forgerock.openig.handler.Handlers;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EndpointRegistryTest {

    @DataProvider
    public static Object[][] names() {
        // @Checkstyle:off
        return new Object[][] {
                { "routes", "/openig/api/routes" },
                { "/routes", "/openig/api/routes" },
                { "routes/", "/openig/api/routes" },
                { "/routes/", "/openig/api/routes" },
                { "/routes/sub", "/openig/api/routes/sub" }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "names")
    public void shouldComputePath(final String name, final String path) throws Exception {
        EndpointRegistry registry = new EndpointRegistry(new Router(), "/openig/api");
        EndpointRegistry.Registration registration = registry.register(name, Handlers.NO_CONTENT);

        assertThat(registration.getPath()).isEqualTo(path);
    }
}
