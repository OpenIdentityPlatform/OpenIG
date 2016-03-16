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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.services.context.ClientContext.buildExternalClientContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.handler.router.DestroyDetectHandler;
import org.forgerock.openig.handler.router.Files;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.AfterMethod;
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

    @AfterMethod
    public void tearDown() throws Exception {
        DestroyDetectHandler.destroyed = false;
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
        promise = start.handle(buildLocalContext(), request);
        assertThat(promise.get().getStatus()).isNotEqualTo(Status.TEAPOT);

        // This request must be handled by the root handler
        request = new Request();
        request.setUri("/foo");
        promise = start.handle(buildExternalContext(), request);
        assertThat(promise.get().getStatus()).isEqualTo(Status.TEAPOT);
    }

    @Test
    public void shouldDisallowOpenIgEndpointsAccessToExternalClient() throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "teapot"));
        GatewayHttpApplication application = new GatewayHttpApplication(env);
        Handler handler = application.start();

        Context context = buildExternalContext();
        assertThat(handler.handle(context, new Request().setUri("/openig")).get().getStatus())
                .isEqualTo(Status.FORBIDDEN);
    }

    @Test
    public void shouldProvideOpenIgApiStructure() throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "teapot"));
        GatewayHttpApplication application = new GatewayHttpApplication(env);
        Handler handler = application.start();

        // This request must not be handled by the root handler
        Context context = buildLocalContext();
        assertThat(handler.handle(context, new Request().setUri("/openig")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(handler.handle(context, new Request().setUri("/openig/api")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(handler.handle(context, new Request().setUri("/openig/api/system")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(handler.handle(context, new Request().setUri("/openig/api/system/objects")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
    }
    @Test
    public void shouldStartGatewayAndUseDefaultForgeRockClientHandler() throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "forgerock_client_handler"));
        new GatewayHttpApplication(env).start();
    }
    @Test
    public void shouldUseDefaultConfigWhenConfigJsonIsMissing() throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "default-config"));
        GatewayHttpApplication application = new GatewayHttpApplication(env);
        Handler handler = application.start();

        // Make sure that GET / returns the welcome page
        Context context = buildExternalContext();
        Response response = handler.handle(context, new Request().setMethod("GET").setUri("/")).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(response.getEntity().getString()).contains("Welcome to Open Identity Gateway");
    }

    @Test
    public void shouldSupportConfigurationOfEndpointProtection() throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "protection"));
        GatewayHttpApplication application = new GatewayHttpApplication(env);
        Handler handler = application.start();

        // The new filter expects an 'access_token=ae32f' parameter
        Context context = buildExternalContext();
        assertThat(handler.handle(context, new Request().setUri("/openig")).get().getStatus())
                .isEqualTo(Status.UNAUTHORIZED);

        assertThat(handler.handle(context, new Request().setUri("/openig?access_token=ae32f")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
    }

    @Test
    public void shouldStartGatewayAndUseDefaultScheduledServiceExecutor() throws Exception {
        Environment env = new DefaultEnvironment(Files.getRelative(getClass(), "executor"));
        new GatewayHttpApplication(env).start();
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

    public static class ExecutorServiceHandler extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            // Will throw an exception if missing
            heap.get(SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY, ScheduledExecutorService.class);
            return new ResponseHandler(Status.OK);
        }
    }
}
