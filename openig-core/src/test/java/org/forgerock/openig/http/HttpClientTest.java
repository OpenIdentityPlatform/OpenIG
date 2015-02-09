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

import static com.xebialabs.restito.builder.stub.StubHttp.*;
import static com.xebialabs.restito.semantics.Action.*;
import static com.xebialabs.restito.semantics.Condition.*;
import static java.lang.String.*;
import static org.assertj.core.api.Assertions.*;

import org.forgerock.http.Request;
import org.forgerock.openig.io.TemporaryStorage;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.xebialabs.restito.semantics.Call;
import com.xebialabs.restito.semantics.Condition;
import com.xebialabs.restito.server.StubServer;

@SuppressWarnings("javadoc")
public class HttpClientTest {

    private StubServer server;

    @BeforeTest
    public void setUp() throws Exception {
        // Create mock HTTP server.
        server = new StubServer().run();
    }

    @AfterTest
    public void tearDown() throws Exception {
        server.stop();
    }

    @BeforeMethod
    public void cleanup() throws Exception {
        // Clear mocked invocations between tests
        // So we can reuse the server instance (less traces) still having isolation
        if (server != null) {
            server.getCalls().clear();
            server.getStubs().clear();
        }
    }

    @Test
    public void shouldSendPostHttpMessageWithEntityContent() throws Exception {
        whenHttp(server).match(post("/test"),
                               withPostBodyContaining("Hello"))
                        .then(status(HttpStatus.OK_200));

        HttpClient client = new HttpClient(new TemporaryStorage());

        Request request = new Request();
        request.setMethod("POST");
        request.setUri(format("http://localhost:%d/test", server.getPort()));
        request.getEntity().setString("Hello");

        assertThat(client.execute(request).getStatus()).isEqualTo(200);

    }

    @Test
    public void shouldSendPostHttpMessageWithEmptyEntity() throws Exception {
        whenHttp(server).match(post("/test"),
                               not(withPostBody()))
                        .then(status(HttpStatus.OK_200));

        HttpClient client = new HttpClient(new TemporaryStorage());

        Request request = new Request();
        request.setMethod("POST");
        request.setUri(format("http://localhost:%d/test", server.getPort()));

        assertThat(client.execute(request).getStatus()).isEqualTo(200);
    }

    /**
     * Restito doesn't provide any way to express a negative condition yet.
     */
    private static Condition not(final Condition condition) {
        return new MyCondition(Predicates.not(condition.getPredicate()));
    }

    /**
     * And Condition has a unique protected constructor.
     */
    private static class MyCondition extends Condition {
        protected MyCondition(final Predicate<Call> predicate) {
            super(predicate);
        }
    }
}
