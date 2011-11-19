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
import java.io.File;
import java.io.IOException;
import java.util.Map;

// JSON Fluent
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.text.Separators;
import org.forgerock.openig.text.SeparatedValuesFile;
import org.forgerock.openig.util.JsonValueUtil;
import org.forgerock.openig.util.LazyMap;

/**
 * Retrieves and exposes a record from a delimier-separated file. Lookup of the record is
 * performed using a specified key, whose value is derived from an exchange-scoped expression.
 * The resulting record is exposed in a {@link Map} object, whose location is specified by the
 * {@code target} expression. If a matching record cannot be found, then the resulting map
 * will be empty.
 * <p>
 * The retrieval of the record is performed lazily; it does not occur until the first attempt
 * to access a value in the target. This defers the overhead of file operations and text
 * processing until a value is first required. This also means that the {@code value}
 * expression will not be evaluated until the map is first accessed.
 *
 * @author Paul C. Bryan
 * @see SeparatedValuesFile
 */
public class FileAttributesFilter extends GenericFilter {

    /** Expression that yields the target object that will contain the record. */
    public Expression target;

    /** The file to read separated values from. */
    public final SeparatedValuesFile file = new SeparatedValuesFile();

    /** The name of the field in the file to perform the lookup on. */
    public String key;

    /** Expression that yields the value to be looked-up within the file. */
    public Expression value;

    /**
     * Filters the exchange by putting a lazily initialized map in the object referenced by
     * the {@code target} expression.
     */
    @Override
    public void filter(final Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        target.set(exchange, new LazyMap<String, String>() {
            @Override protected Map<String, String> init() {
                try {
                    return file.getRecord(key, value.eval(exchange).toString());
                } catch (IOException ioe) {
                    logger.warning(ioe);
                    return null; // results in an empty map
                }
            }
        });
        next.handle(exchange);
        timer.stop();
    }

    /** Creates and initializes a separated values file attribute provider in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override public Object create() throws HeapException, JsonValueException {
            FileAttributesFilter filter = new FileAttributesFilter();
            filter.target = JsonValueUtil.asExpression(config.get("target").required());
            filter.file.file = new File(config.get("file").required().asString());
            filter.file.charset = config.get("charset").defaultTo("UTF-8").asCharset();
            filter.file.separator = config.get("separator").defaultTo("COMMA").asEnum(Separators.class).separator;
            filter.file.header = config.get("header").defaultTo(true).asBoolean();
            filter.file.fields = config.get("fields").asList(String.class);
            filter.key = config.get("key").required().asString();
            filter.value = JsonValueUtil.asExpression(config.get("value").required());
            return filter;
        }
    }
}
