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
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// JSON Fluent
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.handler.StaticResponseHandler;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.*;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.util.JsonValueUtil;

/**
 * Removes headers from and adds headers to a message.
 *
 * @author Paul C. Bryan
 */
public class HeaderFilter extends GenericFilter {

    /** Indicates the type of message in the exchange to filter headers for. */
    MessageType messageType;

    /** The names of header fields to remove from the message. */
    public final CaseInsensitiveSet remove = new CaseInsensitiveSet();

    /** Header fields to add to the message. */
    public final Headers add = new Headers();

    /**
     * Removes all specified headers, then adds all specified headers.
     *
     * @param message the message to remove headers from and add headers to.
     */
    private void process(Message message, Exchange exchange) {
        for (String s : this.remove) {
            message.headers.remove(s);
        }
        for (String key : this.add.keySet()) {
            for (String value : this.add.get(key)) {
                JsonValue jsonValue = new JsonValue(value);
                message.headers.add(key, (String)JsonValueUtil.asExpression(jsonValue).eval(exchange));
            }
        }
    }

    /**
     * Filters the request and/or response of an exchange by removing headers from and adding
     * headers to a message.
     */
    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        if (messageType == MessageType.REQUEST) {
            process(exchange.request, exchange);
        }
        next.handle(exchange);
        if (messageType == MessageType.RESPONSE) {
            process(exchange.response, exchange);
        }
        timer.stop();
    }

    /** Creates and initializes a header filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override public Object create() throws HeapException, JsonValueException {
            HeaderFilter filter = new HeaderFilter();
            filter.messageType = config.get("messageType").required().asEnum(MessageType.class); // required
            filter.remove.addAll(config.get("remove").defaultTo(Collections.emptyList()).asList(String.class)); // optional
            JsonValue add = config.get("add").defaultTo(Collections.emptyMap()).expect(Map.class); // optional
            for (String key : add.keys()) {
                List<String> values = add.get(key).required().asList(String.class);
                filter.add.addAll(key, values);
            }
            return filter;
        }
    }
}
