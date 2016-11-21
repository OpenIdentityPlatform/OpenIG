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
import static org.forgerock.openig.Files.getRelative;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.readJson;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.handler.router.DestroyDetectHandler;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class GatewayHttpApplicationTest {

    @AfterMethod
    public void tearDown() throws Exception {
        DestroyDetectHandler.destroyed = false;
    }

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
            Environment env = new DefaultEnvironment(getRelative(getClass(), configName));
            GatewayHttpApplication application = new GatewayHttpApplication(env, gatewayConfig(env), null);
            application.start();
            failBecauseExceptionWasNotThrown(HttpApplicationException.class);
        } catch (HttpApplicationException e) {
            assertThat(DestroyDetectHandler.destroyed).isTrue();
        }
    }

    @Test
    public void shouldStartGatewayAndUseDefaultForgeRockClientHandler() throws Exception {
        Environment env = new DefaultEnvironment(getRelative(getClass(), "forgerock_client_handler"));
        new GatewayHttpApplication(env, gatewayConfig(env), null).start();
    }

    @Test
    public void shouldStartGatewayAndUseDefaultScheduledServiceExecutor() throws Exception {
        Environment env = new DefaultEnvironment(getRelative(getClass(), "executor"));
        new GatewayHttpApplication(env, gatewayConfig(env), null).start();
    }

    private static JsonValue gatewayConfig(Environment env) throws IOException {
        return readJson(new File(env.getConfigDirectory(), "config.json").toURI().toURL());
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
