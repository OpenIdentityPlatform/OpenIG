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
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.Requests.newCreateRequest;
import static org.forgerock.json.resource.Requests.newDeleteRequest;
import static org.forgerock.json.resource.Requests.newQueryRequest;
import static org.forgerock.json.resource.Requests.newReadRequest;
import static org.forgerock.json.resource.Resources.newCollection;
import static org.forgerock.json.resource.Resources.newInternalConnection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.regex.Pattern;

import org.forgerock.services.context.Context;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.query.QueryFilter;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ShareCollectionProviderTest {

    private static final String PAT = "88cc69f6-65eb-4a88-8a63-498653c95b06";
    private static final Share SHARE = new Share(null, json(object()), Pattern.compile("/aa"), null);
    private static final String SHARE_ID = SHARE.getId();

    @Mock
    private Context context;

    @Mock
    private UmaSharingService service;

    @Mock
    private QueryResourceHandler queryResourceHandler;

    @Captor
    private ArgumentCaptor<ResourceResponse> captor;

    private Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        connection = newInternalConnection(newCollection(new ShareCollectionProvider(service)));
    }

    @Test
    public void shouldCreateShare() throws Exception {
        when(service.createShare("/alice/allergies", PAT))
                .thenReturn(Promises.<Share, UmaException>newResultPromise(SHARE));

        ResourceResponse resource = connection.create(context,
                                                      newCreateRequest("",
                                                                       json(object(field("path", "/alice/allergies"),
                                                                                   field("pat", PAT)))));

        assertThat(resource.getContent().get("id").asString()).isEqualTo(SHARE_ID);
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldFailShareCreationWhenResourceIdIsProvidedCreateShare() throws Exception {
        connection.create(context, newCreateRequest("", "user-provided-resource-id", new JsonValue(null)));
    }

    @Test(expectedExceptions = ResourceException.class)
    public void shouldReturnBadRequestExceptionWhenShareCreationFailed() throws Exception {
        when(service.createShare("/alice/allergies", PAT))
                .thenReturn(Promises.<Share, UmaException>newExceptionPromise(new UmaException("Boom")));

        connection.create(context, newCreateRequest("",
                                                    json(object(field("path", "/alice/allergies"),
                                                                field("pat", PAT)))));
    }

    @Test
    public void shouldReadShare() throws Exception {
        when(service.getShare(SHARE_ID)).thenReturn(SHARE);

        ResourceResponse resource = connection.read(context, newReadRequest(SHARE_ID));

        assertThat(resource.getContent().get("id").asString()).isEqualTo(SHARE_ID);
    }

    @Test
    public void shouldDeleteShare() throws Exception {
        when(service.removeShare(SHARE_ID)).thenReturn(SHARE);
        ResourceResponse resource = connection.delete(context, newDeleteRequest(SHARE_ID));

        assertThat(resource.getId()).isEqualTo(SHARE_ID);
        assertThat(resource.getContent().get("id").asString()).isEqualTo(SHARE_ID);
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldFailWhenDeletedShareIsUnknown() throws Exception {
        connection.delete(context, newDeleteRequest("share-id"));
    }

    @Test
    public void shouldListShares() throws Exception {
        when(service.listShares()).thenReturn(singleton(SHARE));
        QueryResponse result = connection.query(context,
                                              newQueryRequest("").setQueryFilter(QueryFilter.<JsonPointer>alwaysTrue()),
                                              queryResourceHandler);

        assertThat(result).isNotNull();
        verify(queryResourceHandler).handleResource(captor.capture());

        assertThat(captor.getValue().getId()).isEqualTo(SHARE_ID);
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldFailBecauseQueryExpressionIsProvided() throws Exception {
        connection.query(context,
                         newQueryRequest("").setQueryExpression(""),
                         queryResourceHandler);
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldFailBecauseQueryFilterIsNotTrue() throws Exception {
        connection.query(context,
                         newQueryRequest("").setQueryFilter(QueryFilter.<JsonPointer>alwaysFalse()),
                         queryResourceHandler);
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldFailBecauseQueryIdIsProvided() throws Exception {
        connection.query(context,
                         newQueryRequest("").setQueryId("a-query-id"),
                         queryResourceHandler);
    }
}
