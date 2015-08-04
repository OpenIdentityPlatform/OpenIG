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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.matches;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
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
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));
    }

    @Test
    public void testSqlResultRowIsStoredInAMapAndInAnExchangeProperty() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                Expression.valueOf("${exchange.attributes.result}", Map.class), null);

        mockDatabaseInteractions();

        Exchange exchange = new Exchange();
        filter.filter(exchange, null, terminalHandler);

        // The expression has stored the Map result as an entry in the Exchange's backing map
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) exchange.getAttributes().get("result");
        assertThat(result).containsOnly(entry("password", "secret"));
    }

    @Test
    public void testParametersAreAssignedToTheRightPlaceholders() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                Expression.valueOf("${exchange.attributes.result}", Map.class), null);

        filter.getParameters().add(Expression.valueOf("${true}", Boolean.class));
        filter.getParameters().add(Expression.valueOf("${false}", Boolean.class));

        mockDatabaseInteractions();
        when(pmetadata.getParameterCount()).thenReturn(2);

        Exchange exchange = new Exchange();
        filter.filter(exchange, null, terminalHandler);

        // Trigger the lazy map instantiation
        exchange.getAttributes().get("result").hashCode();

        // Check the placeholders have been replaced by the appropriate values
        InOrder inOrder = inOrder(statement);
        inOrder.verify(statement).setObject(1, Boolean.TRUE);
        inOrder.verify(statement).setObject(2, Boolean.FALSE);
    }

    @Test
    public void testSomethingBadHappenDuringSqlInteraction() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                Expression.valueOf("${exchange.attributes.result}", Map.class), null);
        filter.setLogger(spy(filter.getLogger()));

        // Generate an SQLException when getConnection() is called
        when(source.getConnection()).thenThrow(new SQLException("Unexpected"));

        Exchange exchange = new Exchange();
        filter.filter(exchange, null, terminalHandler);

        // There should be a 'result' entry that is an empty map
        // And the logger should have been invoked with the caught exception
        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) exchange.getAttributes().get("result");
        assertThat(result).isEmpty();
        verify(filter.getLogger()).error(any(SQLException.class));
    }

    @Test
    public void testTooMuchParametersProvided() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                Expression.valueOf("${exchange.attributes.result}", Map.class), null);
        filter.setLogger(spy(filter.getLogger()));

        filter.getParameters().add(Expression.valueOf("${true}", Boolean.class));
        filter.getParameters().add(Expression.valueOf("${false}", Boolean.class));

        mockDatabaseInteractions();
        when(pmetadata.getParameterCount()).thenReturn(0);

        Exchange exchange = new Exchange();
        filter.filter(exchange, null, terminalHandler);

        // Trigger the lazy map instantiation
        exchange.getAttributes().get("result").hashCode();
        verify(filter.getLogger()).warning(matches(" All parameters with index >= 0 are ignored.*"));
    }

    @Test
    public void testNotEnoughParameters() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter(source,
                Expression.valueOf("${exchange.attributes.result}", Map.class), null);
        filter.setLogger(spy(filter.getLogger()));

        filter.getParameters().add(Expression.valueOf("${true}", Boolean.class));
        filter.getParameters().add(Expression.valueOf("${false}", Boolean.class));

        mockDatabaseInteractions();
        when(pmetadata.getParameterCount()).thenReturn(3);

        Exchange exchange = new Exchange();
        filter.filter(exchange, null, terminalHandler);

        // Trigger the lazy map instantiation
        exchange.getAttributes().get("result").hashCode();
        verify(filter.getLogger()).warning(matches(" Placeholder 3 has no provided value as parameter"));
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
