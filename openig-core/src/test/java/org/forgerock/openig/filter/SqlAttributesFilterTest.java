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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.LeftValueExpression;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SqlAttributesFilterTest {

    @Mock
    private DataSource source;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private ResultSetMetaData metadata;

    @Mock
    private ParameterMetaData pmetadata;

    @Mock
    private Handler terminalHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(terminalHandler.handle(any(Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response(Status.OK)));
    }

    @Test
    public void testSqlResultRowIsStoredInAMapAndInAttributesContextProperty() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                LeftValueExpression.valueOf("${attributes.result}", Map.class), null);

        mockDatabaseInteractions();

        AttributesContext context = new AttributesContext(new RootContext());
        filter.filter(context, null, terminalHandler);

        // The expression has stored the Map result as an entry in the Context's attributes
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) context.getAttributes().get("result");
        assertThat(result).containsOnly(entry("password", "secret"));
    }

    @Test
    public void testParametersAreAssignedToTheRightPlaceholders() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                LeftValueExpression.valueOf("${attributes.result}", Map.class), null);

        filter.getParameters().add(Expression.valueOf("${true}", Boolean.class));
        filter.getParameters().add(Expression.valueOf("${false}", Boolean.class));

        mockDatabaseInteractions();
        when(pmetadata.getParameterCount()).thenReturn(2);

        AttributesContext context = new AttributesContext(new RootContext());
        filter.filter(context, null, terminalHandler);

        // Trigger the lazy map instantiation
        context.getAttributes().get("result").hashCode();

        // Check the placeholders have been replaced by the appropriate values
        InOrder inOrder = inOrder(statement);
        inOrder.verify(statement).setObject(1, Boolean.TRUE);
        inOrder.verify(statement).setObject(2, Boolean.FALSE);
    }

    @Test
    public void testSomethingBadHappenDuringSqlInteraction() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                LeftValueExpression.valueOf("${attributes.result}", Map.class), null);

        // Generate an SQLException when getConnection() is called
        when(source.getConnection()).thenThrow(new SQLException("Unexpected"));

        AttributesContext context = new AttributesContext(new RootContext());
        filter.filter(context, null, terminalHandler);

        // There should be a 'result' entry that is an empty map
        // And the logger should have been invoked with the caught exception
        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) context.getAttributes().get("result");
        assertThat(result).isEmpty();
    }

    @Test
    public void testTooMuchParametersProvided() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                LeftValueExpression.valueOf("${attributes.result}", Map.class), null);

        filter.getParameters().add(Expression.valueOf("${true}", Boolean.class));
        filter.getParameters().add(Expression.valueOf("${false}", Boolean.class));

        mockDatabaseInteractions();
        when(pmetadata.getParameterCount()).thenReturn(0);

        AttributesContext context = new AttributesContext(new RootContext());
        filter.filter(context, null, terminalHandler);

        // Trigger the lazy map instantiation
        context.getAttributes().get("result").hashCode();
    }

    @Test
    public void testNotEnoughParameters() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                LeftValueExpression.valueOf("${attributes.result}", Map.class), null);

        filter.getParameters().add(Expression.valueOf("${true}", Boolean.class));
        filter.getParameters().add(Expression.valueOf("${false}", Boolean.class));

        mockDatabaseInteractions();
        when(pmetadata.getParameterCount()).thenReturn(3);

        AttributesContext context = new AttributesContext(new RootContext());
        filter.filter(context, null, terminalHandler);

        // Trigger the lazy map instantiation
        context.getAttributes().get("result").hashCode();
    }

    private void mockDatabaseInteractions() throws Exception {
        // Mock the database interactions
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(null)).thenReturn(statement);
        when(statement.getParameterMetaData()).thenReturn(pmetadata);
        when(pmetadata.getParameterCount()).thenReturn(0);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("password");
        when(resultSet.getObject(1)).thenReturn("secret");
    }

}
