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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.filter;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.HeapUtilsTest;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.util.JsonValues.readJson;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

public class MCPServerFeaturesFilterTest {

    private HeapImpl heap;
    private JsonValue config;

    @Mock
    Handler testHandler;

    private AutoCloseable closeable;

    @BeforeMethod
    public void setUp() throws Exception {

        closeable = MockitoAnnotations.openMocks(this);

        heap = HeapUtilsTest.buildDefaultHeap();

        config = json(object());
        config.put("allow", object(
                field("tools",  array("current_time_service")),
                field("prompts", array("prompt1", "prompt2"))));

        config.put("deny", object(
                field("resources",  array("res1")),
                field("resources/templates",  array("resource_template1"))));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        closeable.close();
    }


    @Test
    public void testFilterSetup() throws HeapException {

        MCPServerFeaturesFilter filter = (MCPServerFeaturesFilter) new MCPServerFeaturesFilter
                .Heaplet().create(Name.of("this"), config, heap);

        assertThat(filter.getAllowFeatures()).isNotNull();
        assertThat(filter.getDenyFeatures()).isNotNull();

        assertThat(filter.getAllowFeatures().get(MCPServerFeaturesFilter.MCPFeature.TOOLS)).hasSize(1);
        assertThat(filter.getAllowFeatures().get(MCPServerFeaturesFilter.MCPFeature.PROMPTS)).hasSize(2);
        assertThat(filter.getDenyFeatures().get(MCPServerFeaturesFilter.MCPFeature.RESOURCES)).hasSize(1);
        assertThat(filter.getDenyFeatures().get(MCPServerFeaturesFilter.MCPFeature.RESOURCES_TEMPLATES)).hasSize(1);
    }

    @Test
    public void testToolsListRestriction() throws Exception {
        Request req = new Request();
        JsonValue jsonReq = readJson(this.getClass().getClassLoader()
                .getResource("org/openidentityplatrform/openig/mcp/tools-list-req.json"));
        req.setEntity(jsonReq.toString());

        MCPServerFeaturesFilter filter = (MCPServerFeaturesFilter) new MCPServerFeaturesFilter
                .Heaplet().create(Name.of("this"), config, heap);

        Context ctx = new RootContext();

        when(testHandler.handle(ctx, req))
                .then((Answer<Promise<Response, NeverThrowsException>>) invocation -> {
                    JsonValue jsonResp = readJson(this.getClass().getClassLoader()
                            .getResource("org/openidentityplatrform/openig/mcp/tools-list-resp.json"));
                    Response resp = new Response(Status.OK);
                    resp.setEntity(jsonResp.toString());
                    return Promises.newResultPromise(resp);
                });

        Response response = filter.filter(ctx, req, testHandler).get();

        JsonValue result = json(response.getEntity().getJson()).get("result");

        List<JsonValue> toolsList = result.get("tools").asList()
                .stream().map(JsonValue::json).collect(Collectors.toList());

        assertTrue(toolsList.stream().anyMatch(t -> t.get("name").asString().equals("current_time_service")));
        assertTrue(toolsList.stream().noneMatch(t -> t.get("name").asString().equals("set_current_time_service")));
    }

    @Test
    public void testAllowedToolCall() throws Exception {
        Request req = new Request();
        JsonValue jsonReq = readJson(this.getClass().getClassLoader()
                .getResource("org/openidentityplatrform/openig/mcp/allowed-tool-call-req.json"));
        req.setEntity(jsonReq.toString());

        MCPServerFeaturesFilter filter = (MCPServerFeaturesFilter) new MCPServerFeaturesFilter
                .Heaplet().create(Name.of("this"), config, heap);

        Context ctx = new RootContext();

        when(testHandler.handle(ctx, req))
                .then((Answer<Promise<Response, NeverThrowsException>>) invocation -> {
                    JsonValue jsonResp = readJson(this.getClass().getClassLoader()
                            .getResource("org/openidentityplatrform/openig/mcp/allowed-tool-call-resp.json"));
                    Response resp = new Response(Status.OK);
                    resp.setEntity(jsonResp.toString());
                    return Promises.newResultPromise(resp);
                });

        Response response = filter.filter(ctx, req, testHandler).get();
        JsonValue result = json(response.getEntity().getJson()).get("result");
        assertThat(result.get("isError").asBoolean()).isFalse();
        assertThat(result.get("content").asList()).isNotEmpty();
    }

    @Test
    public void testDeniedToolCall() throws Exception {
        Request req = new Request();
        JsonValue jsonReq = readJson(this.getClass().getClassLoader()
                .getResource("org/openidentityplatrform/openig/mcp/disallowed-tool-call-req.json"));
        req.setEntity(jsonReq.toString());

        MCPServerFeaturesFilter filter = (MCPServerFeaturesFilter) new MCPServerFeaturesFilter
                .Heaplet().create(Name.of("this"), config, heap);

        Context ctx = new RootContext();

        when(testHandler.handle(ctx, req))
                .then((Answer<Promise<Response, NeverThrowsException>>) invocation -> {
                    JsonValue jsonResp = json(object(field("content", array()), field("isError", false)));
                    Response resp = new Response(Status.OK);
                    resp.setEntity(jsonResp.toString());
                    return Promises.newResultPromise(resp);
                });

        Response response = filter.filter(ctx, req, testHandler).get();
        JsonValue responseEntity = json(response.getEntity().getJson());
        assertThat(responseEntity.get("error").get("code").asInteger()).isNotZero();
        assertThat(responseEntity.get("error").get("message").asString()).contains("invalid_tool_name");
    }
}