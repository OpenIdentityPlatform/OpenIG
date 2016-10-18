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

package org.forgerock.openig.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.http.RunMode.EVALUATION;
import static org.forgerock.openig.http.RunMode.PRODUCTION;
import static org.forgerock.openig.util.JsonValues.readJson;
import static org.forgerock.services.context.ClientContext.buildExternalClientContext;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.handler.router.Files;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AdminHttpApplicationTest {

    private static final String ADMIN_PREFIX = "/openig";

    @Test
    public void shouldHonorApiProtectionFilterIfSpecified() throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "protection"));
        AdminHttpApplication application = new AdminHttpApplication(ADMIN_PREFIX, adminConfig(env), env, EVALUATION);
        Handler handler = application.start();

        // The new filter expects an 'access_token=ae32f' parameter
        Context context = buildExternalContext();
        assertThat(handler.handle(context, new Request().setUri("/")).get().getStatus())
                .isEqualTo(Status.UNAUTHORIZED);

        assertThat(handler.handle(context, new Request().setUri("/?access_token=ae32f")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
    }

    @DataProvider
    public static Object[][] modes() {
        // @Checkstyle:off
        return new Object[][] {
                { EVALUATION, Status.NO_CONTENT },
                { PRODUCTION, Status.FORBIDDEN }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "modes")
    public void shouldProtectOrNotTheEndpointsWhenNoApiProtectionFilterIfSpecified(RunMode mode, Status status)
            throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "ignored"));
        AdminHttpApplication application = new AdminHttpApplication(ADMIN_PREFIX, json(object()), env, mode);
        Handler handler = application.start();

        // Build a request "from the outside" that is accepted by default in eval mode and rejected in prod mode
        Context context = buildExternalContext();
        Request request = new Request().setUri("/");
        assertThat(handler.handle(context, request).get().getStatus())
                .isEqualTo(status);
    }

    @Test
    public void shouldProvideOpenIgApiStructure() throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "ignored"));
        AdminHttpApplication application = new AdminHttpApplication(ADMIN_PREFIX, json(object()), env, EVALUATION);
        Handler handler = application.start();

        // This request must not be handled by the root handler
        Context context = buildLocalContext();
        assertThat(handler.handle(context, new Request().setUri("/")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(handler.handle(context, new Request().setUri("/api")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(handler.handle(context, new Request().setUri("/api/system")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(handler.handle(context, new Request().setUri("/api/system/objects")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
    }

    private JsonValue adminConfig(Environment env) throws IOException {
        return readJson(new File(env.getConfigDirectory(), "admin.json").toURI().toURL());
    }

    private static Context buildLocalContext() {
        return ClientContext.buildExternalClientContext(new RootContext())
                            .remoteAddress("127.0.0.1")
                            .build();
    }

    private Context buildExternalContext() {
        ClientContext clientInfoContext = buildExternalClientContext(new RootContext())
                .certificates()
                .remoteAddress("125.12.34.52")
                .build();
        return new AttributesContext(new SessionContext(clientInfoContext, new SimpleMapSession()));
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException { }
    }

    public static class PseudoOAuth2Filter implements Filter {

        @Override
        public Promise<Response, NeverThrowsException> filter(final Context context,
                                                              final Request request,
                                                              final Handler next) {
            Form form = request.getForm();
            if (form.containsKey("access_token")) {
                if ("ae32f".equals(form.getFirst("access_token"))) {
                    return next.handle(context, request);
                }
            }
            return newResponsePromise(new Response(Status.UNAUTHORIZED));
        }

        public static class Heaplet extends GenericHeaplet {

            @Override
            public Object create() throws HeapException {
                return new PseudoOAuth2Filter();
            }
        }
    }
}
