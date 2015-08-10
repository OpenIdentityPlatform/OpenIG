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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import java.io.IOException;
import java.util.HashMap;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.Session;
import org.forgerock.http.context.ClientInfoContext;
import org.forgerock.http.context.HttpRequestContext;
import org.forgerock.http.context.RootContext;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.handler.router.DestroyDetectHandler;
import org.forgerock.openig.handler.router.Files;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class GatewayHttpApplicationTest {

    @DataProvider
    public static Object[][] invalidConfigurations() {
        // @Checkstyle:off
        return new Object[][] {
                { "failure_on_startup" },
                { "unknown_type" }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "invalidConfigurations")
    public void shouldStopTheHeapInCaseOfInvalidConfiguration(String configName) throws Exception {
        try {
            Environment env = new DefaultEnvironment(Files.getRelative(getClass(), configName));
            GatewayHttpApplication application = new GatewayHttpApplication(env);
            application.start();
            failBecauseExceptionWasNotThrown(HttpApplicationException.class);
        } catch (HttpApplicationException e) {
            assertThat(DestroyDetectHandler.destroyed).isTrue();
        }
    }

    @Test
    public void shouldReserveOpenIGPath() throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "teapot"));
        GatewayHttpApplication application = new GatewayHttpApplication(env);
        Handler start = application.start();

        Promise<Response, NeverThrowsException> promise;
        Request request;

        // This request must not be handled by the root handler
        request = new Request();
        request.setUri("/openig");
        promise = start.handle(new RootContext(), request);
        assertThat(promise.get().getStatus()).isNotEqualTo(Status.TEAPOT);

        // This request must be handled by the root handler
        request = new Request();
        request.setUri("/foo");
        promise = start.handle(buildContext(), request);
        assertThat(promise.get().getStatus()).isEqualTo(Status.TEAPOT);
    }

    private HttpRequestContext buildContext() {
        ClientInfoContext clientInfoContext = ClientInfoContext.builder(new RootContext()).certificates().build();
        HttpRequestContext context = new HttpRequestContext(clientInfoContext, new SimpleMapSession());
        return context;
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException { }
    }
}
