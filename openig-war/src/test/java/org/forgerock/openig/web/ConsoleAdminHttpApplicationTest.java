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

package org.forgerock.openig.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.web.OpenIGInitializerTest.getRelative;
import static org.forgerock.services.context.ClientContext.newInternalClientContext;

import java.util.Collections;

import org.forgerock.http.Handler;
import org.forgerock.http.header.ContentTypeHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConsoleAdminHttpApplicationTest {

    @Test
    public void shouldServeTheUi() throws Exception {
        Environment env = new DefaultEnvironment(getRelative(getClass(), "doesnt-exist"));
        ConsoleAdminHttpApplication module = new ConsoleAdminHttpApplication("openig", json(object()), env);
        Handler handler = module.start();

        Response response = handler.handle(newInternalClientContext(newUriRouterContext(new RootContext())),
                                           new Request().setMethod("GET").setUri("/console/")).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(response.getHeaders().getFirst(ContentTypeHeader.class)).isEqualTo("text/html");
    }

    private static UriRouterContext newUriRouterContext(Context parent) {
        return new UriRouterContext(parent,
                                    "",
                                    "",
                                    Collections.<String, String>emptyMap());
    }
}
