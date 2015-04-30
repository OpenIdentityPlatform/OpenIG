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

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Condition.get;
import static com.xebialabs.restito.semantics.Condition.post;
import static com.xebialabs.restito.semantics.Condition.withPostBody;
import static com.xebialabs.restito.semantics.Condition.withPostBodyContaining;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Status;
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

        HttpClient client = new HttpClient();
        try {
            Request request = new Request();
            request.setMethod("POST");
            request.setUri(format("http://localhost:%d/test", server.getPort()));
            request.getEntity().setString("Hello");
            assertThat(client.execute(request).getStatus()).isEqualTo(Status.OK);
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void shouldSendPostHttpMessageWithEmptyEntity() throws Exception {
        whenHttp(server).match(post("/test"),
                               not(withPostBody()))
                        .then(status(HttpStatus.OK_200));

        HttpClient client = new HttpClient();
        try {
            Request request = new Request();
            request.setMethod("POST");
            request.setUri(format("http://localhost:%d/test", server.getPort()));
            assertThat(client.execute(request).getStatus()).isEqualTo(Status.OK);
        } finally {
            client.shutdown();
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void shouldNotBeAbleToExecuteRequestAfterShutdown() throws Exception {
        whenHttp(server).match(get("/test")).then(status(HttpStatus.OK_200));

        HttpClient client = new HttpClient();
        client.shutdown();

        Request request = new Request();
        request.setMethod("GET");
        request.setUri(format("http://localhost:%d/test", server.getPort()));

        // Should throw an Exception as we closed the client before.
        client.execute(request);
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
