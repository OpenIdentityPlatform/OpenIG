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

package org.forgerock.openig.handler.resources;

import static org.forgerock.http.header.HeaderUtil.formatDate;
import static org.forgerock.http.header.HeaderUtil.parseDate;
import static org.forgerock.http.io.IO.newBranchingInputStream;
import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Status.FOUND;
import static org.forgerock.http.protocol.Status.METHOD_NOT_ALLOWED;
import static org.forgerock.http.protocol.Status.NOT_FOUND;
import static org.forgerock.http.protocol.Status.OK;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.forgerock.http.Handler;
import org.forgerock.http.header.ContentTypeHeader;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * A {@link ResourceHandler} is a handler that serves static content (content of a directory, or a zip).
 *
 * <p>It's using the remaining URL information provided by the {@link UriRouterContext} to determine
 * the resource path to look for.
 */
public class ResourceHandler implements Handler {

    /**
     * {@literal Not Modified} 304 Status.
     */
    @VisibleForTesting
    static final Status NOT_MODIFIED = Status.valueOf(304, "Not Modified");

    private final List<ResourceSet> resourceSets;
    private final List<String> welcomePages;
    private final Factory<Buffer> storage;

    @VisibleForTesting
    ResourceHandler(final List<ResourceSet> sets) {
        this(newTemporaryStorage(), sets, Collections.<String>emptyList());
    }

    /**
     * Creates a new {@link ResourceHandler} with the given {@code sets} of {@link ResourceSet} and the
     * list of welcome pages mappings.
     * @param storage the temporary storage to use to stream the resource
     * @param sets provide access to {@link Resource}.
     * @param welcomePages the list of resources name to be searched if there is
     *        no remaining path to use in the request.
     */
    public ResourceHandler(final Factory<Buffer> storage,
                           final List<ResourceSet> sets,
                           final List<String> welcomePages) {
        this.storage = storage;
        this.resourceSets = sets;
        this.welcomePages = welcomePages;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        // Reject any non-GET methods
        if (!"GET".equals(request.getMethod())) {
            return newResponsePromise(new Response(METHOD_NOT_ALLOWED));
        }

        // Get the resource path to look for
        UriRouterContext urc = context.asContext(UriRouterContext.class);
        String target = urc.getRemainingUri();

        Resource resource = null;
        if ("".equals(target)) {
            // Workaround the issue that Router ignore leading '/' in remainingUri
            // preventing /resource/path to work like /resource/path/ for instance
            if (!request.getUri().getPath().endsWith("/")) {
                // Force redirect to the 'slashed' version of the URL
                Response redirect = new Response(FOUND);
                redirect.getHeaders().add(new LocationHeader(request.getUri().toString() + "/"));
                return newResponsePromise(redirect);
            }
            // Need welcome page
            // Find the first matching resource
            Iterator<String> i = welcomePages.iterator();
            while (i.hasNext() && resource == null) {
                String welcomePage = i.next();
                resource = findResource(welcomePage);
            }
        } else {
            resource = findResource(target);
        }

        if (resource != null) {
            // cached in client ?
            String since = request.getHeaders().getFirst("If-Modified-Since");
            if (since != null) {
                if (!resource.hasChangedSince(parseDate(since).getTime())) {
                    return newResponsePromise(new Response(NOT_MODIFIED));
                }
            }

            // not cached, need to send the content back
            Response response = new Response(OK);
            // last modified
            response.getHeaders().put("Last-Modified", formatDate(new Date(resource.getLastModified())));
            try {
                response.getEntity().setRawContentInputStream(newBranchingInputStream(resource.open(), storage));
            } catch (IOException e) {
                return newResponsePromise(newInternalServerError(e));
            }

            // content-type
            String mediaType = resource.getType();
            if (mediaType != null) {
                response.getHeaders().put(ContentTypeHeader.NAME, mediaType);
            }

            return newResponsePromise(response);
        }
        return newResponsePromise(new Response(NOT_FOUND));
    }

    private Resource findResource(final String path) {
        // Test path in every root and return the first match
        Resource resource = null;
        Iterator<ResourceSet> i = resourceSets.iterator();
        while (i.hasNext() && resource == null) {
            ResourceSet resourceSet = i.next();
            resource = resourceSet.find(path);
        }
        return resource;
    }
}
