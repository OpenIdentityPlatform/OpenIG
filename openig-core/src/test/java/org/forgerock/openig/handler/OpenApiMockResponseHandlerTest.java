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

package org.forgerock.openig.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

/**
 * Unit tests for {@link OpenApiMockResponseHandler}.
 */
public class OpenApiMockResponseHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PETSTORE_SPEC = ""
            + "openapi: '3.0.3'\n"
            + "info:\n"
            + "  title: Petstore\n"
            + "  version: '1.0.0'\n"
            + "paths:\n"
            + "  /pets:\n"
            + "    get:\n"
            + "      summary: List all pets\n"
            + "      responses:\n"
            + "        '200':\n"
            + "          description: A list of pets\n"
            + "          content:\n"
            + "            application/json:\n"
            + "              schema:\n"
            + "                type: array\n"
            + "                items:\n"
            + "                  type: object\n"
            + "                  properties:\n"
            + "                    id:\n"
            + "                      type: integer\n"
            + "                    name:\n"
            + "                      type: string\n"
            + "                      example: doggie\n"
            + "                    status:\n"
            + "                      type: string\n"
            + "                      enum: [available, pending, sold]\n"
            + "    post:\n"
            + "      summary: Create a pet\n"
            + "      responses:\n"
            + "        '201':\n"
            + "          description: Created\n"
            + "          content:\n"
            + "            application/json:\n"
            + "              schema:\n"
            + "                type: object\n"
            + "                properties:\n"
            + "                  id:\n"
            + "                    type: integer\n"
            + "                  name:\n"
            + "                    type: string\n"
            + "  /pets/{petId}:\n"
            + "    get:\n"
            + "      summary: Get pet by ID\n"
            + "      responses:\n"
            + "        '200':\n"
            + "          description: A pet\n"
            + "          content:\n"
            + "            application/json:\n"
            + "              schema:\n"
            + "                type: object\n"
            + "                properties:\n"
            + "                  id:\n"
            + "                    type: integer\n"
            + "                  name:\n"
            + "                    type: string\n"
            + "                    example: doggie\n";

    private OpenApiMockResponseHandler handler;
    private RootContext context;

    @BeforeMethod
    public void setUp() {
        handler  = new OpenApiMockResponseHandler(PETSTORE_SPEC, 200, 2);
        context  = new RootContext();
    }

    // -----------------------------------------------------------------------
    // Basic path matching
    // -----------------------------------------------------------------------

    @Test
    public void handle_returns200_forMatchingGetRequest() throws Exception {
        final Response response = handler.handle(context, getRequest("/pets")).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void handle_returns404_forUnmatchedPath() throws Exception {
        final Response response = handler.handle(context, getRequest("/unknown")).get();
        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
    }

    @Test
    public void handle_returns405_forWrongMethod() throws Exception {
        final Response response = handler.handle(context, deleteRequest("/pets")).get();
        assertThat(response.getStatus()).isEqualTo(Status.METHOD_NOT_ALLOWED);
    }

    // -----------------------------------------------------------------------
    // Path-parameter matching
    // -----------------------------------------------------------------------

    @Test
    public void handle_returns200_forPathWithParameter() throws Exception {
        final Response response = handler.handle(context, getRequest("/pets/42")).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void handle_returns404_forPathThatDoesNotMatchParameter() throws Exception {
        // /pets/42/photos is not declared in the spec
        final Response response = handler.handle(context, getRequest("/pets/42/photos")).get();
        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
    }

    // -----------------------------------------------------------------------
    // JSON body
    // -----------------------------------------------------------------------

    @Test
    public void handle_setsContentTypeHeader() throws Exception {
        final Response response = handler.handle(context, getRequest("/pets")).get();
        assertThat(response.getHeaders().getFirst("Content-Type")).contains("application/json");
    }

    @Test
    public void handle_returnsValidJson() throws Exception {
        final Response response = handler.handle(context, getRequest("/pets")).get();
        final String body = response.getEntity().getString();
        assertThat(body).isNotNull().isNotBlank();
        // Should not throw
        MAPPER.readTree(body);
    }

    @Test
    public void handle_returnsArray_whenSchemaTypeIsArray() throws Exception {
        final Response response = handler.handle(context, getRequest("/pets")).get();
        final String body = response.getEntity().getString();
        assertThat(body).startsWith("[");
        final List<?> list = MAPPER.readValue(body, List.class);
        assertThat(list).hasSize(2); // arraySize=2
    }

    @Test
    @SuppressWarnings("unchecked")
    public void handle_usesExampleValue_fromSchema() throws Exception {
        final Response response = handler.handle(context, getRequest("/pets/42")).get();
        final String body = response.getEntity().getString();
        final Map<String, Object> obj = MAPPER.readValue(body, Map.class);
        // "name" has example: doggie
        assertThat(obj.get("name")).isEqualTo("doggie");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void handle_usesFirstEnumValue_whenEnumIsDefined() throws Exception {
        final Response response = handler.handle(context, getRequest("/pets")).get();
        final String body = response.getEntity().getString();
        final List<Map<String, Object>> list = MAPPER.readValue(body, List.class);
        assertThat(list).isNotEmpty();
        // "status" has enum: [available, pending, sold] - first value should be used
        assertThat(list.get(0).get("status")).isEqualTo("available");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void handle_returns201_forPostRequest() throws Exception {
        final OpenApiMockResponseHandler postHandler =
                new OpenApiMockResponseHandler(PETSTORE_SPEC, 201, 1);
        final Response response = postHandler.handle(context, postRequest("/pets", "{}")).get();
        assertThat(response.getStatus()).isEqualTo(Status.valueOf(201));
        final Map<String, Object> obj = MAPPER.readValue(response.getEntity().getString(), Map.class);
        assertThat(obj).containsKey("id");
        assertThat(obj).containsKey("name");
    }

    // -----------------------------------------------------------------------
    // pathMatches
    // -----------------------------------------------------------------------

    @Test
    public void pathMatches_returnsFalse_forNullArguments() {
        assertThat(OpenApiMockResponseHandler.pathMatches(null, "/pets")).isFalse();
        assertThat(OpenApiMockResponseHandler.pathMatches("/pets", null)).isFalse();
    }

    @Test
    public void pathMatches_returnsTrue_forExactMatch() {
        assertThat(OpenApiMockResponseHandler.pathMatches("/pets", "/pets")).isTrue();
    }

    @Test
    public void pathMatches_returnsTrue_forParameterisedTemplate() {
        assertThat(OpenApiMockResponseHandler.pathMatches("/pets/{id}", "/pets/42")).isTrue();
        assertThat(OpenApiMockResponseHandler.pathMatches("/pets/{id}", "/pets/fluffy")).isTrue();
    }

    @Test
    public void pathMatches_returnsFalse_forMismatch() {
        assertThat(OpenApiMockResponseHandler.pathMatches("/pets/{id}", "/orders/42")).isFalse();
        assertThat(OpenApiMockResponseHandler.pathMatches("/pets/{id}", "/pets")).isFalse();
        assertThat(OpenApiMockResponseHandler.pathMatches("/pets/{id}", "/pets/42/photos")).isFalse();
    }

    // -----------------------------------------------------------------------
    // bestResponseSchema
    // -----------------------------------------------------------------------

    @Test
    public void bestResponseSchema_prefers200OverOthers() {
        final Operation op = buildOperation("200", "201", "202");
        final Schema<?> schema = OpenApiMockResponseHandler.bestResponseSchema(op);
        assertThat(schema).isNotNull();
        // The 200 schema should be selected
        assertThat(schema.getDescription()).isEqualTo("schema-200");
    }

    @Test
    public void bestResponseSchema_falls_to201_when200IsAbsent() {
        final Operation op = buildOperation("201", "202");
        final Schema<?> schema = OpenApiMockResponseHandler.bestResponseSchema(op);
        assertThat(schema).isNotNull();
        assertThat(schema.getDescription()).isEqualTo("schema-201");
    }

    @Test
    public void bestResponseSchema_returnsNull_whenNoResponseSchema() {
        final Operation op = new Operation();
        final ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", new ApiResponse().description("OK")); // no content/schema
        op.setResponses(responses);
        assertThat(OpenApiMockResponseHandler.bestResponseSchema(op)).isNull();
    }

    @Test
    public void bestResponseSchema_returnsNull_forNullResponses() {
        final Operation op = new Operation();
        assertThat(OpenApiMockResponseHandler.bestResponseSchema(op)).isNull();
    }

    // -----------------------------------------------------------------------
    // generateBody
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void generateBody_generatesObject_fromObjectSchema() {
        final Schema<Object> schema = new Schema<>();
        schema.setType("object");
        final Map<String, Schema> props = new LinkedHashMap<>();
        final Schema<String> nameSchema = new Schema<>();
        nameSchema.setType("string");
        props.put("name", nameSchema);
        schema.setProperties(props);

        final Object body = handler.generateBody(schema);
        assertThat(body).isInstanceOf(Map.class);
        final Map<String, Object> map = (Map<String, Object>) body;
        assertThat(map).containsKey("name");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void generateBody_generatesArray_fromArraySchema() {
        final ArraySchema schema = new ArraySchema();
        final Schema<String> items = new Schema<>();
        items.setType("string");
        schema.setItems(items);

        final Object body = handler.generateBody(schema);
        assertThat(body).isInstanceOf(List.class);
        final List<?> list = (List<?>) body;
        assertThat(list).hasSize(2); // arraySize=2
    }

    @Test
    public void generateBody_returnsNull_forNullSchema() {
        assertThat(handler.generateBody(null)).isNull();
    }

    // -----------------------------------------------------------------------
    // Heaplet
    // -----------------------------------------------------------------------

    @Test
    public void heaplet_createsHandler_withValidSpec() throws Exception {
        final HeapImpl heap = new HeapImpl(Name.of("test"));
        final JsonValue config = json(object(
                field("spec", PETSTORE_SPEC),
                field("defaultStatusCode", 200),
                field("arraySize", 3)));

        final Object created = new OpenApiMockResponseHandler.Heaplet()
                .create(Name.of("testMock"), config, heap);

        assertThat(created).isInstanceOf(OpenApiMockResponseHandler.class);
    }

    // -----------------------------------------------------------------------
    // Nested schema generation
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void handle_generatesNestedObjects() throws Exception {
        final String nestedSpec = ""
                + "openapi: '3.0.3'\n"
                + "info:\n"
                + "  title: Nested\n"
                + "  version: '1'\n"
                + "paths:\n"
                + "  /users:\n"
                + "    get:\n"
                + "      responses:\n"
                + "        '200':\n"
                + "          description: OK\n"
                + "          content:\n"
                + "            application/json:\n"
                + "              schema:\n"
                + "                type: object\n"
                + "                properties:\n"
                + "                  id:\n"
                + "                    type: integer\n"
                + "                  address:\n"
                + "                    type: object\n"
                + "                    properties:\n"
                + "                      city:\n"
                + "                        type: string\n"
                + "                      zip:\n"
                + "                        type: string\n";

        final OpenApiMockResponseHandler nestedHandler =
                new OpenApiMockResponseHandler(nestedSpec, 200, 1);
        final Response response = nestedHandler.handle(context, getRequest("/users")).get();

        assertThat(response.getStatus()).isEqualTo(Status.OK);
        final Map<String, Object> body = MAPPER.readValue(response.getEntity().getString(), Map.class);
        assertThat(body).containsKey("address");
        final Map<String, Object> address = (Map<String, Object>) body.get("address");
        assertThat(address).containsKey("city");
        assertThat(address).containsKey("zip");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Request getRequest(final String path) throws Exception {
        final Request r = new Request();
        r.setMethod("GET");
        r.setUri("http://localhost" + path);
        return r;
    }

    private static Request deleteRequest(final String path) throws Exception {
        final Request r = new Request();
        r.setMethod("DELETE");
        r.setUri("http://localhost" + path);
        return r;
    }

    private static Request postRequest(final String path, final String body) throws Exception {
        final Request r = new Request();
        r.setMethod("POST");
        r.setUri("http://localhost" + path);
        r.getHeaders().put("Content-Type", "application/json");
        r.setEntity(body);
        return r;
    }

    /**
     * Builds an Operation that has a response entry for each supplied status code.
     * Each response's schema has a {@code description} set to {@code "schema-<code>"}
     * so tests can identify which schema was selected.
     */
    private static Operation buildOperation(final String... codes) {
        final Operation op = new Operation();
        final ApiResponses responses = new ApiResponses();
        for (final String code : codes) {
            final Schema<Object> schema = new Schema<>();
            schema.setDescription("schema-" + code);
            final MediaType mt = new MediaType();
            mt.setSchema(schema);
            final Content content = new Content();
            content.addMediaType("application/json", mt);
            final ApiResponse ar = new ApiResponse();
            ar.setContent(content);
            responses.addApiResponse(code, ar);
        }
        op.setResponses(responses);
        return op;
    }
}
