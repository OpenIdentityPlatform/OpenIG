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

package org.forgerock.openig.uma;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.JsonValue.set;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.Router;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Keys;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class UmaSharingServiceTest {

    private static final String PAT = "00D50000000IZ3Z!AQ0AQDpEDKYsn7ioKug2aSmgCjgrPjG9eRLza8jXWoW7u"
            + "A90V39rvQaIy1FGxjFHN1ZtusBGljncdEi8eRiuit1QdQ1Z2KSV";

    private static final String RESOURCE_SET_CREATED = "{\n"
            + "  \"_id\": \"e99016bb-b8f1-4e42-b83c-b0be67baf0fd0\",\n"
            + "  \"user_access_policy_uri\": \"https://as.example"
            + ".com:8443/openam/XUI/?realm=/#uma/share/e99016bb-b8f1-4e42-b83c-b0be67baf0fd0\"\n"
            + "}";

    private static final String RESOURCE_SET_CREATED_2 = "{\n"
            + "  \"_id\": \"157bc0e4-3d6c-4e3e-ab0a-11370372d37f\",\n"
            + "  \"user_access_policy_uri\": \"https://as.example"
            + ".com/policy?rsid=/#uma/share/157bc0e4-3d6c-4e3e-ab0a-11370372d37f\"\n"
            + "}";

    @Mock
    private Handler handler;
    private UmaSharingService service;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldCreateShare() throws Exception {
        Response response1 = new Response(Status.CREATED);
        response1.setEntity(RESOURCE_SET_CREATED);

        Response response2 = new Response(Status.CREATED);
        response2.setEntity(RESOURCE_SET_CREATED_2);

        when(handler.handle(any(Context.class), any(Request.class)))
                .thenReturn(newResponsePromise(response1))
                .thenReturn(newResponsePromise(response2));

        ShareTemplate template = new ShareTemplate(Pattern.compile("/alice/allergies.*"),
                                                   singletonList(
                                                           createAction("http://uma.example.com/allergies#read")));
        service = new UmaSharingService(handler,
                                        singletonList(template),
                                        new URI("http://localhost"),
                                        "uma",
                                        "uma");

        Share share = service.createShare(new RootContext(), "/alice/allergies", PAT).getOrThrow();

        assertThat(share.getPAT()).isEqualTo(PAT);
        assertThat(share.getTemplate()).isSameAs(template);
        assertThat(share.getResourceSetId()).isEqualTo("e99016bb-b8f1-4e42-b83c-b0be67baf0fd0");
        assertThat(share.getUserAccessPolicyUri())
                .isEqualTo("https://as.example.com:8443/openam/XUI/"
                                   + "?realm=/#uma/share/e99016bb-b8f1-4e42-b83c-b0be67baf0fd0");
    }

    private ShareTemplate.Action createAction(final String scope) throws Exception {
        return new ShareTemplate.Action(Expression.valueOf("${true}", Boolean.class), singleton(scope));
    }

    @Test(expectedExceptions = UmaException.class)
    public void shouldFailBecauseAuthorisationServerCantCreateResourceSet() throws Exception {
        Response response = new Response(Status.NOT_FOUND);
        when(handler.handle(any(Context.class), any(Request.class)))
                .thenReturn(newResponsePromise(response));

        ShareTemplate template = new ShareTemplate(Pattern.compile("/alice/allergies.*"),
                                                   singletonList(
                                                           createAction("http://uma.example.com/allergies#read")));
        UmaSharingService service = new UmaSharingService(handler,
                                                          singletonList(template),
                                                          new URI("http://localhost"),
                                                          "uma",
                                                          "uma");

        service.createShare(new RootContext(), "/alice/allergies", PAT).getOrThrow();
    }

    @Test(dependsOnMethods = "shouldCreateShare",
          expectedExceptions = UmaException.class)
    public void shouldNotShareTheSameResourceAgain() throws Exception {
        service.createShare(new RootContext(), "/alice/allergies", PAT).getOrThrow();
    }

    @Test(dependsOnMethods = "shouldCreateShare",
          expectedExceptions = UmaException.class)
    public void shouldNotShareResourceWithNoAssociatedTemplate() throws Exception {
        service.createShare(new RootContext(), "/bob/heart", PAT).getOrThrow();
    }

    @Test(dependsOnMethods = "shouldCreateShare")
    public void shouldFindShare() throws Exception {
        Request request = new Request();
        request.setUri("http://localhost/alice/allergies");
        Share share = service.findShare(request);
        assertThat(share).isNotNull();
    }

    @Test(dependsOnMethods = "shouldCreateShare")
    public void shouldListSingleShare() throws Exception {
        assertThat(service.listShares()).hasSize(1);
    }

    @Test(dependsOnMethods = "shouldCreateShare",
          expectedExceptions = UmaException.class)
    public void shouldNotFindShare() throws Exception {
        Request request = new Request();
        request.setUri("http://localhost/alice/allergies/pollen");
        service.findShare(request);
    }

    @Test(dependsOnMethods = "shouldCreateShare")
    public void shouldFindBestShare() throws Exception {
        // Before: Add a second share (more specific)
        Share newShare = service.createShare(new RootContext(), "/alice/allergies/pollen", PAT)
                                .getOrThrow();
        try {
            Request request = new Request();
            request.setUri("http://localhost/alice/allergies/pollen");
            Share share = service.findShare(request);
            assertThat(share.getResourceSetId()).isEqualTo("157bc0e4-3d6c-4e3e-ab0a-11370372d37f");
        } finally {
            service.removeShare(newShare.getId());
        }
    }

    @Test(dependsOnMethods = "shouldCreateShare")
    public void shouldGetShareByID() throws Exception {
        Share share = service.listShares().iterator().next();
        assertThat(service.getShare(share.getId())).isNotNull();
    }

    @Test
    public void shouldRegisterUmaShareEndpoint() throws Exception {
        Router router = new Router();
        HeapImpl heap = new HeapImpl(Name.of("this"));
        heap.put(Keys.LOGSINK_HEAP_KEY, new NullLogSink());
        heap.put(Keys.TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
        heap.put(Keys.ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(router));
        heap.put("#mock-handler", handler);

        when(handler.handle(any(Context.class), any(Request.class)))
                .thenReturn(newResponsePromise(new Response(Status.CREATED).setEntity(object())));

        List<Object> resources = array(object(field("pattern", ".*"),
                                              field("actions", array(object(field("condition", "${true}"),
                                                                            field("scopes", set("scope-a")))))));
        JsonValue config = json(object(field("protectionApiHandler", "#mock-handler"),
                                       field("authorizationServerUri", "http://openam.example.com"),
                                       field("clientId", "joe"),
                                       field("clientSecret", "s3cr3t"),
                                       field("resources", resources)));
        new UmaSharingService.Heaplet().create(Name.of("uma"), config, heap);

        // The endpoint should be registered in /uma/share
        //  * 'uma' being the heap object name
        //  * 'share' being the registered endpoint
        Request request = new Request().setMethod("POST").setUri("/uma/share?_action=create");
        request.getEntity().setJson(object(field("pat", PAT),
                                           field("path", "/alice/allergies")));
        Context context = new AttributesContext(new RootContext());
        Response response = router.handle(context, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.CREATED);
    }
}
