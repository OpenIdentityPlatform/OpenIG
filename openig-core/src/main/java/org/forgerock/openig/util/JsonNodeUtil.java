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

package org.forgerock.openig.util;

// Java Standard Edition
import java.util.HashMap;

// JSON Fluent
import org.forgerock.json.fluent.JsonNode;
import org.forgerock.json.fluent.JsonNodeException;

// OpenIG Core
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;

/**
 * Provides additional functionality to JsonNode.
 *
 * @author Paul C. Bryan
 */ 
public class JsonNodeUtil {

    /** TODO: Description. */
    private static final HashMap<String, String> aliases = new HashMap<String, String>();

// TODO: allow aliases to be dynamically configured 
    static {
        aliases.put("AssignmentFilter", "org.forgerock.openig.filter.AssignmentFilter");
        aliases.put("CaptureFilter", "org.forgerock.openig.filter.CaptureFilter");
        aliases.put("Chain", "org.forgerock.openig.filter.Chain");
        aliases.put("ClientHandler", "org.forgerock.openig.handler.ClientHandler");
        aliases.put("ConsoleLogSink", "org.forgerock.openig.log.ConsoleLogSink");
        aliases.put("CookieFilter", "org.forgerock.openig.filter.CookieFilter");
        aliases.put("CryptoHeaderFilter", "org.forgerock.openig.filter.CryptoHeaderFilter");
        aliases.put("DispatchHandler", "org.forgerock.openig.handler.DispatchHandler");
        aliases.put("DispatchServlet", "org.forgerock.openig.servlet.DispatchServlet");
        aliases.put("EntityExtractFilter", "org.forgerock.openig.filter.EntityExtractFilter");
        aliases.put("ExceptionFilter", "org.forgerock.openig.filter.ExceptionFilter");
        aliases.put("FileAttributesFilter", "org.forgerock.openig.filter.FileAttributesFilter");
        aliases.put("HandlerServlet", "org.forgerock.openig.servlet.HandlerServlet");
        aliases.put("HeaderFilter", "org.forgerock.openig.filter.HeaderFilter");
        aliases.put("HttpBasicAuthFilter", "org.forgerock.openig.filter.HttpBasicAuthFilter");
        aliases.put("SequenceHandler", "org.forgerock.openig.handler.SequenceHandler");
        aliases.put("SqlAttributesFilter", "org.forgerock.openig.filter.SqlAttributesFilter");
        aliases.put("StaticRequestFilter", "org.forgerock.openig.filter.StaticRequestFilter");
        aliases.put("StaticResponseHandler", "org.forgerock.openig.handler.StaticResponseHandler");
        aliases.put("SwitchFilter", "org.forgerock.openig.filter.SwitchFilter");
        aliases.put("TemporaryStorage", "org.forgerock.openig.io.TemporaryStorage");
    }

    /**
     * TODO: Description.
     *
     * @param node TODO.
     * @return TODO.
     * @throws JsonNodeException if value is not a string or the named class could not be found.
     */
    private static Class classForName(JsonNode node) throws JsonNodeException {
        String c = node.asString();
        String a = aliases.get(c);
        if (a != null) {
            c = a;
        }
        try {
            return Class.forName(c, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException cnfe) {
            throw new JsonNodeException(node, cnfe);
        }
    }

    /**
     * Returns the class object associated with a named class or interface, using the thread
     * context class loader. If the value is {@code null}, this method returns {@code null}.
     *
     * @return the class object with the specified name.
     * @throws JsonNodeException if value is not a string or the named class could not be found.
     */
    public static Class asClass(JsonNode node) throws JsonNodeException {
        return (node == null || node.isNull()? null : classForName(node));
    }

    /**
     * Creates a new instance of a named class. The class is instantiated as if by a
     * {@code new} expression with an empty argument list. The class is initialized if it has
     * not already been initialized. If the value is {@code null}, this method returns
     * {@code null}.
     *
     * @param node the node containing the class name string.
     * @param type the type that the instantiated class should to resolve to.
     * @return a new instance of the requested class.
     * @throws JsonNodeException if the requested class could not be instantiated.
     */
    @SuppressWarnings("unchecked")
    public static <T> T asNewInstance(JsonNode node, Class<T> type) throws JsonNodeException {
        if (node == null || node.isNull()) {
            return null;
        }
        Class c = asClass(node);
        if (!type.isAssignableFrom(c)) {
            throw new JsonNodeException(node, "expecting " + type.getName()); 
        }
        try {
            return (T)c.newInstance();
        } catch (ExceptionInInitializerError eiie) {
            throw new JsonNodeException(node, eiie);
        } catch (IllegalAccessException iae) {
            throw new JsonNodeException(node, iae);
        } catch (InstantiationException ie) {
            throw new JsonNodeException(node, ie);
        }
    }

    /**
     * Returns a node string value as an expression. If the value is {@code null}, this
     * method returns {@code null}.
     *
     * @param node the node containing the expression string.
     * @return the expression represented by the string value.
     * @throws JsonNodeException if the value is not a string or the value is not a valid expression.
     */
    public static Expression asExpression(JsonNode node) throws JsonNodeException {
        try {
            return (node == null || node.isNull() ? null : new Expression(node.asString()));
        } catch (ExpressionException ee) {
            throw new JsonNodeException(node, ee);
        }
    }
}
