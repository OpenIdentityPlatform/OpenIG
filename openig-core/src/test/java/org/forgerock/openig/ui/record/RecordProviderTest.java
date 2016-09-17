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

package org.forgerock.openig.ui.record;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.Requests.newActionRequest;
import static org.forgerock.json.resource.Requests.newCreateRequest;
import static org.forgerock.json.resource.Requests.newDeleteRequest;
import static org.forgerock.json.resource.Requests.newPatchRequest;
import static org.forgerock.json.resource.Requests.newQueryRequest;
import static org.forgerock.json.resource.Requests.newReadRequest;
import static org.forgerock.json.resource.Requests.newUpdateRequest;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PreconditionFailedException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.util.query.QueryFilter;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RecordProviderTest {

    private static final String ID = "resourceId";
    private static final String PATH = "";

    private static final String REV_0 = "0";
    private static final String REV_1 = "1";
    private static final String REV_42 = "42 (unknown revision)";

    private static final Record RECORD = new Record(ID, REV_0, json(object()));
    private static final Record UPDATED_RECORD = new Record(ID, REV_1, json(object()));

    @Mock
    private RecordService service;
    private RecordProvider provider;
    @Mock
    private QueryResourceHandler queryResourceHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        provider = new RecordProvider(service);
    }

    @Test
    public void shouldCreateInstance() throws Exception {
        when(service.create(any(JsonValue.class))).thenReturn(RECORD);

        CreateRequest createRequest = newCreateRequest(PATH, null, json(object()));
        ResourceResponse response = provider.createInstance(null, createRequest)
                                            .getOrThrow();

        assertThat(response.getId()).isEqualTo(ID);
        assertThat(response.getRevision()).isEqualTo(REV_0);
        assertThat(response.getContent()).isEmpty();
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void shouldFailWithProvidedResourceIdCreateInstance() throws Exception {
        CreateRequest createRequest = newCreateRequest(PATH, ID, json(null));
        provider.createInstance(null, createRequest)
                .getOrThrow();
    }

    @Test(expectedExceptions = InternalServerErrorException.class)
    public void shouldFailWhenIoErrorDuringCreateInstance() throws Exception {
        when(service.create(any(JsonValue.class))).thenThrow(new IOException());

        CreateRequest createRequest = newCreateRequest(PATH, null, json(null));
        provider.createInstance(null, createRequest)
                .getOrThrow();
    }

    @Test
    public void shouldDeleteInstanceWithMatchingRevision() throws Exception {
        when(service.delete(ID, REV_0)).thenReturn(RECORD);

        DeleteRequest deleteRequest = newDeleteRequest(PATH).setRevision(REV_0);
        ResourceResponse response = provider.deleteInstance(null, ID, deleteRequest)
                                            .getOrThrow();
        assertThat(response.getId()).isEqualTo(ID);
        assertThat(response.getRevision()).isEqualTo(REV_0);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    public void shouldDeleteInstanceWithNoProvidedRevision() throws Exception {
        when(service.delete(ID, null)).thenReturn(RECORD);

        DeleteRequest deleteRequest = newDeleteRequest(PATH);
        ResourceResponse response = provider.deleteInstance(null, ID, deleteRequest)
                                            .getOrThrow();
        assertThat(response.getId()).isEqualTo(ID);
        assertThat(response.getRevision()).isEqualTo(REV_0);
        assertThat(response.getContent()).isEmpty();
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldNotDeleteInstanceWhenResourceNotFound() throws Exception {
        DeleteRequest deleteRequest = newDeleteRequest(PATH);
        provider.deleteInstance(null, ID, deleteRequest)
                .getOrThrow();
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void shouldNotDeleteInstanceWhenResourceAndRevisionNotFound() throws Exception {
        when(service.delete(ID, REV_42)).thenThrow(new RecordException("boom"));

        DeleteRequest deleteRequest = newDeleteRequest(PATH).setRevision(REV_42);
        provider.deleteInstance(null, ID, deleteRequest)
                .getOrThrow();
    }

    @Test(expectedExceptions = InternalServerErrorException.class)
    public void shouldNotDeleteInstanceWhenIoErrorOccurs() throws Exception {
        when(service.delete(ID, null)).thenThrow(new IOException());

        DeleteRequest deleteRequest = newDeleteRequest(PATH);
        provider.deleteInstance(null, ID, deleteRequest)
                .getOrThrow();
    }

    @Test
    public void shouldQueryCollection() throws Exception {
        when(service.listAll()).thenReturn(singleton(RECORD));

        QueryRequest queryRequest = newQueryRequest(PATH).setQueryFilter(QueryFilter.<JsonPointer>alwaysTrue());
        QueryResponse response = provider.queryCollection(null, queryRequest, queryResourceHandler)
                                         .getOrThrow();

        assertThat(response).isNotNull();
        verify(queryResourceHandler).handleResource(any(ResourceResponse.class));
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldNotQueryCollectionWhenQueryIdIsUsed() throws Exception {
        try {
            QueryRequest queryRequest = newQueryRequest(PATH).setQueryId("queryId");
            provider.queryCollection(null, queryRequest, queryResourceHandler)
                    .getOrThrow();
        } finally {
            verifyZeroInteractions(queryResourceHandler);
        }
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldNotQueryCollectionWhenQueryExpressionIsUsed() throws Exception {
        try {
            QueryRequest queryRequest = newQueryRequest(PATH).setQueryExpression("queryExpression");
            provider.queryCollection(null, queryRequest, queryResourceHandler)
                    .getOrThrow();
        } finally {
            verifyZeroInteractions(queryResourceHandler);
        }
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldNotQueryCollectionWhenNonTrueQueryFilterIsUsed() throws Exception {
        try {
            QueryRequest queryRequest = newQueryRequest(PATH).setQueryFilter(QueryFilter.<JsonPointer>alwaysFalse());
            provider.queryCollection(null, queryRequest, queryResourceHandler)
                    .getOrThrow();
        } finally {
            verifyZeroInteractions(queryResourceHandler);
        }
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldNotQueryCollectionWhenNoQueryIsSpecified() throws Exception {
        try {
            QueryRequest queryRequest = newQueryRequest(PATH);
            provider.queryCollection(null, queryRequest, queryResourceHandler)
                    .getOrThrow();
        } finally {
            verifyZeroInteractions(queryResourceHandler);
        }
    }

    @Test(expectedExceptions = InternalServerErrorException.class)
    public void shouldNotQueryCollectionWhenIoErrorOccurs() throws Exception {
        try {
            when(service.listAll()).thenThrow(new IOException());

            QueryRequest queryRequest = newQueryRequest(PATH).setQueryFilter(QueryFilter.<JsonPointer>alwaysTrue());
            provider.queryCollection(null, queryRequest, queryResourceHandler)
                    .getOrThrow();
        } finally {
            verifyZeroInteractions(queryResourceHandler);
        }
    }

    @Test
    public void shouldReadInstance() throws Exception {
        when(service.find(ID)).thenReturn(RECORD);

        ReadRequest readRequest = newReadRequest(PATH);
        ResourceResponse response = provider.readInstance(null, ID, readRequest)
                                            .getOrThrow();

        assertThat(response.getId()).isEqualTo(ID);
        assertThat(response.getRevision()).isEqualTo(REV_0);
        assertThat(response.getContent()).isEmpty();
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldNotReadInstanceWhenResourceNotFound() throws Exception {
        when(service.find(ID)).thenReturn(null);

        ReadRequest readRequest = newReadRequest(PATH);
        provider.readInstance(null, ID, readRequest)
                .getOrThrow();
    }

    @Test(expectedExceptions = InternalServerErrorException.class)
    public void shouldNotReadInstanceWhenIoErrorOccurs() throws Exception {
        when(service.find(ID)).thenThrow(new IOException());

        ReadRequest readRequest = newReadRequest(PATH);
        provider.readInstance(null, ID, readRequest)
                .getOrThrow();
    }

    @Test
    public void shouldUpdateInstanceWithProvidedRevision() throws Exception {
        when(service.update(eq(ID), eq(REV_0), any(JsonValue.class)))
                .thenReturn(UPDATED_RECORD);

        UpdateRequest updateRequest = newUpdateRequest(PATH, json(object())).setRevision(REV_0);
        ResourceResponse response = provider.updateInstance(null, ID, updateRequest)
                                            .getOrThrow();

        assertThat(response.getId()).isEqualTo(ID);
        assertThat(response.getRevision()).isEqualTo(REV_1);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    public void shouldUpdateInstanceWithProvidedCatchAllRevision() throws Exception {
        when(service.update(eq(ID), isNull(String.class), any(JsonValue.class)))
                .thenReturn(UPDATED_RECORD);

        // HttpAdapter transform If-Match "*" in a null revision
        UpdateRequest updateRequest = newUpdateRequest(PATH, json(object())).setRevision(null);
        ResourceResponse response = provider.updateInstance(null, ID, updateRequest)
                                            .getOrThrow();

        assertThat(response.getId()).isEqualTo(ID);
        assertThat(response.getRevision()).isEqualTo(REV_1);
        assertThat(response.getContent()).isEmpty();
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldNotUpdateInstanceWhenResourceIdNotFound() throws Exception {
        UpdateRequest updateRequest = newUpdateRequest(PATH, json(object()));
        provider.updateInstance(null, ID, updateRequest)
                .getOrThrow();
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void shouldNotUpdateInstanceWhenRevisionDoesNotMatch() throws Exception {
        when(service.update(eq(ID), eq(REV_42), any(JsonValue.class)))
                .thenThrow(new RecordException("boom"));

        UpdateRequest updateRequest = newUpdateRequest(PATH, json(object())).setRevision(REV_42);
        provider.updateInstance(null, ID, updateRequest)
                .getOrThrow();
    }

    @Test(expectedExceptions = InternalServerErrorException.class)
    public void shouldNotUpdateInstanceWhenIoErrorOccurs() throws Exception {
        when(service.update(eq(ID), isNull(String.class), any(JsonValue.class)))
                .thenThrow(new IOException());

        UpdateRequest updateRequest = newUpdateRequest(PATH, json(object()));
        provider.updateInstance(null, ID, updateRequest)
                .getOrThrow();
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldRejectCallToActionCollection() throws Exception {
        provider.actionCollection(null, newActionRequest(PATH, "actionId"))
                .getOrThrow();
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldRejectCallToActionInstance() throws Exception {
        provider.actionInstance(null, ID, newActionRequest(PATH, "actionId"))
                .getOrThrow();
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void shouldRejectCallToPatchInstance() throws Exception {
        provider.patchInstance(null, ID, newPatchRequest(PATH, ID))
                .getOrThrow();
    }
}
