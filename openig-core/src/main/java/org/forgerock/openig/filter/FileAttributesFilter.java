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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static java.lang.String.format;
import static org.forgerock.json.JsonValueFunctions.charset;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.json.JsonValueFunctions.file;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.expression;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.text.SeparatedValuesFile;
import org.forgerock.openig.text.Separators;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.LazyMap;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Retrieves and exposes a record from a delimiter-separated file. Lookup of the record is
 * performed using a specified key, whose value is derived from an expression.
 * The resulting record is exposed in a {@link Map} object, whose location is specified by the
 * {@code target} expression. If a matching record cannot be found, then the resulting map
 * will be empty.
 * <p>
 * The retrieval of the record is performed lazily; it does not occur until the first attempt
 * to access a value in the target. This defers the overhead of file operations and text
 * processing until a value is first required. This also means that the {@code value}
 * expression will not be evaluated until the map is first accessed.
 *
 * @see SeparatedValuesFile
 */
public class FileAttributesFilter extends GenericHeapObject implements Filter {

    /** Expression that yields the target object that will contain the record. */
    @SuppressWarnings("rawtypes") // Can't find the correct syntax to write Expression<Map<String, String>>
    private final Expression<Map> target;

    /** The file to read separated values from. */
    private final SeparatedValuesFile file;

    /** The name of the field in the file to perform the lookup on. */
    private final String key;

    /** Expression that yields the value to be looked-up within the file. */
    private final Expression<String> value;

    /**
     * Builds a new FileAttributesFilter extracting values from the given separated values file.
     *
     * @param file
     *         The file to read separated values from ({@literal csv} file)
     * @param key
     *         The name of the field in the file to perform the lookup on
     * @param value
     *         Expression that yields the value to be looked-up within the file
     * @param target
     *         Expression that yields the target object that will contain the record
     */
    public FileAttributesFilter(final SeparatedValuesFile file,
                                final String key,
                                final Expression<String> value,
                                @SuppressWarnings("rawtypes") final Expression<Map> target) {
        this.file = file;
        this.key = key;
        this.value = value;
        this.target = target;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        final Bindings bindings = bindings(context, request);
        target.set(bindings, new LazyMap<>(new Factory<Map<String, String>>() {
            @Override
            public Map<String, String> newInstance() {
                try {
                    String eval = value.eval(bindings);
                    Map<String, String> record = file.getRecord(key, eval);
                    if (record == null) {
                        logger.debug(format("Couldn't select a row where column %s value is equal to %s", key, eval));
                        return Collections.emptyMap();
                    } else {
                        return record;
                    }
                } catch (IOException ioe) {
                    logger.warning(ioe);
                    // results in an empty map
                    return Collections.emptyMap();
                }
            }
        }));
        return next.handle(context, request);
    }

    /** Creates and initializes a separated values file attribute provider in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            SeparatedValuesFile sources =
                    new SeparatedValuesFile(config.get("file").as(evaluated()).required().as(file()),
                                            config.get("charset").as(evaluated()).defaultTo("UTF-8").as(charset()),
                                            config.get("separator").as(evaluated()).defaultTo("COMMA")
                                                  .as(enumConstant(Separators.class))
                                                  .getSeparator(),
                                            config.get("header").as(evaluated()).defaultTo(true).asBoolean());

            if (config.isDefined("fields")) {
                sources.getFields().addAll(config.get("fields").as(evaluated()).asList(String.class));
            }
            return new FileAttributesFilter(sources,
                                            config.get("key").as(evaluated()).required().asString(),
                                            config.get("value").required().as(expression(String.class)),
                                            config.get("target").required().as(expression(Map.class)));
        }
    }
}
