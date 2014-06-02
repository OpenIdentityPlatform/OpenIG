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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.http.Exchange;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.MapAssert.*;
import static org.mockito.Mockito.*;

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
    private Handler terminalHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSqlResultRowIsStoredInAMapAndInAnExchangeProperty() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter();
        filter.dataSource = source;
        filter.target = new Expression("${exchange.result}");

        mockDatabaseInteractions();

        Exchange exchange = new Exchange();
        filter.filter(exchange, terminalHandler);

        // Verify the terminal handler has been called
        verify(terminalHandler).handle(exchange);
        // The expression has stored the Map result as an entry in the Exchange's backing map
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map) exchange.get("result");
        assertThat(result).contains(entry("password", "secret"));
    }

    @Test
    public void testParametersAreAssignedToTheRightPlaceholders() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter();
        filter.dataSource = source;
        filter.target = new Expression("${exchange.result}");
        filter.parameters.add(new Expression("${true}"));
        filter.parameters.add(new Expression("${false}"));

        mockDatabaseInteractions();

        Exchange exchange = new Exchange();
        filter.filter(exchange, terminalHandler);

        // Trigger the lazy map instantiation
        exchange.get("result").hashCode();

        // Check the placeholders have been replaced by the appropriate values
        InOrder inOrder = inOrder(statement);
        inOrder.verify(statement).setObject(1, Boolean.TRUE);
        inOrder.verify(statement).setObject(2, Boolean.FALSE);
    }

    @Test
    public void testSomethingBadHappenDuringSqlInteraction() throws Exception {
        SqlAttributesFilter filter = new SqlAttributesFilter();
        filter.dataSource = source;
        filter.target = new Expression("${exchange.result}");
        filter.logger = spy(filter.logger);

        // Generate an SQLException when getConnection() is called
        SQLException unexpected = new SQLException("Unexpected");
        when(source.getConnection()).thenThrow(unexpected);

        Exchange exchange = new Exchange();
        filter.filter(exchange, terminalHandler);

        // There should be a 'result' entry that is an empty map
        // And the logger should have been invoked with the caught exception
        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) exchange.get("result");
        assertThat(result).isEmpty();
        verify(filter.logger).warning(unexpected);
    }

    private void mockDatabaseInteractions() throws Exception {
        // Mock the database interactions
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(null)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.first()).thenReturn(true);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("password");
        when(resultSet.getObject(1)).thenReturn("secret");
    }

}
