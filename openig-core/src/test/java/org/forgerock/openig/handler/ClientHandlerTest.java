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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.builder.verify.VerifyHttp.verifyHttp;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Condition.alwaysTrue;
import static com.xebialabs.restito.semantics.Condition.method;
import static com.xebialabs.restito.semantics.Condition.uri;
import static com.xebialabs.restito.semantics.Condition.withPostBodyContaining;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.http.HttpClient;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.testng.annotations.Test;

import com.xebialabs.restito.server.StubServer;

@SuppressWarnings("javadoc")
public class ClientHandlerTest {
    @Test(description = "test for OPENIG-315")
    public void checkRequestIsForwardedUnaltered() throws Exception {
        final StubServer server = new StubServer().run();
        final int port = server.getPort();
        whenHttp(server).match(alwaysTrue()).then(status(HttpStatus.OK_200));
        try {
            Request request = new Request();
            request.setMethod("POST");
            request.setUri("http://0.0.0.0:" + port + "/example");

            final Map<String, Object> json = new LinkedHashMap<String, Object>();
            json.put("k1", "v1");
            json.put("k2", "v2");
            request.setEntity(json);

            final ClientHandler handler = new ClientHandler(new HttpClient());
            Response response = handler.handle(null, request).get();

            assertThat(response.getStatus()).isEqualTo(Status.OK);
            verifyHttp(server).once(method(Method.POST), uri("/example"),
                                    withPostBodyContaining("{\"k1\":\"v1\",\"k2\":\"v2\"}"));
        } finally {
            server.stop();
        }
    }
}
