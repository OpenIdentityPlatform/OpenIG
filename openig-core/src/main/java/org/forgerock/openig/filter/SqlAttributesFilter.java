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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.filter;

// Java Standard Edition

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

// ForgeRock Utilities
import org.forgerock.util.Factory;
import org.forgerock.util.LazyMap;

// JSON Fluent
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogLevel;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.JsonValueUtil;

/**
 * Executes a SQL query through a prepared statement and exposes its first result. Parameters
 * in the prepared statement are derived from exchange-scoped expressions. The query result is
 * exposed in a {@link Map} object, whose location is specified by the {@code target}
 * expression. If the query yields no result, then the resulting map will be empty.
 * <p/>
 * The execution of the query is performed lazily; it does not occur until the first attempt
 * to access a value in the target. This defers the overhead of connection pool, network
 * and database query processing until a value is first required. This also means that the
 * {@code parameters} expressions will not be evaluated until the map is first accessed.
 *
 * @see PreparedStatement
 */
public class SqlAttributesFilter extends GenericFilter {

    /** Expression that yields the target object that will contain the mapped results. */
    public Expression target;

    /** The factory for connections to the physical data source. */
    public DataSource dataSource;

    /** The parametrized SQL query to execute, with ? parameter placeholders. */
    public String preparedStatement;

    /** The list of parameters to evaluate and include in the execution of the prepared statement. */
    public final List<Expression> parameters = new ArrayList<Expression>();

    /**
     * Filters the exchange by putting a lazily initialized map in the object referenced by
     * the {@code target} expression.
     */
    @Override
    public void filter(final Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        target.set(exchange, new LazyMap<String, Object>(new Factory<Map<String, Object>>() {
            @Override
            public Map<String, Object> newInstance() {
                HashMap<String, Object> result = new HashMap<String, Object>();
                Connection c = null;
                try {
                    c = dataSource.getConnection();
                    PreparedStatement ps = c.prepareStatement(preparedStatement); // probably cached in connection pool
                    ps.clearParameters(); // probably unnecessary but a safety precaution
                    Object[] p = new Object[parameters.size()];
                    for (int n = 0; n < p.length; n++) {
                        p[n] = parameters.get(n).eval(exchange);
                    }
                    for (int n = 0; n < p.length; n++) {
                        ps.setObject(n + 1, p[n]);
                    }
                    if (logger.isLoggable(LogLevel.DEBUG)) {
                        logger.debug("Query: " + preparedStatement + ": " + Arrays.toString(p));
                    }
                    ResultSet rs = ps.executeQuery();
                    if (rs.first()) {
                        ResultSetMetaData rsmd = rs.getMetaData();
                        int columns = rsmd.getColumnCount();
                        for (int n = 1; n <= columns; n++) {
                            result.put(rsmd.getColumnLabel(n), rs.getObject(n));
                        }
                    }
                    if (logger.isLoggable(LogLevel.DEBUG)) {
                        logger.debug("Result: " + result);
                    }
                    rs.close();
                    ps.close();
                } catch (SQLException sqle) {
                    logger.warning(sqle); // probably a config issue
                } finally {
                    if (c != null) {
                        try {
                            c.close();
                        } catch (SQLException sqle) {
                            logger.warning(sqle); // probably a network issue
                        }
                    }
                }
                return result;
            }
        }));
        next.handle(exchange);
        timer.stop();
    }

    /** Creates and initializes a static attribute provider in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            SqlAttributesFilter filter = new SqlAttributesFilter();
            filter.target = JsonValueUtil.asExpression(config.get("target").required());
            InitialContext ctx;
            try {
                ctx = new InitialContext();
            } catch (NamingException ne) {
                throw new HeapException(ne);
            }
            JsonValue dataSource = config.get("dataSource").required();
            try {
                filter.dataSource = (DataSource) ctx.lookup(dataSource.asString());
            } catch (NamingException ne) {
                throw new JsonValueException(dataSource, ne);
            } catch (ClassCastException ne) {
                throw new JsonValueException(dataSource, "expecting " + DataSource.class.getName() + " type");
            }
            filter.preparedStatement = config.get("preparedStatement").asString();
            for (JsonValue parameter : config.get("parameters").required().expect(List.class)) {
                filter.parameters.add(JsonValueUtil.asExpression(parameter));
            }
            return filter;
        }
    }
}
