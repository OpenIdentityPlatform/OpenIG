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

package org.forgerock.openig.openam;

import static java.lang.String.format;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.util.Uris.withQuery;
import static org.forgerock.json.JsonValue.array;

import java.net.URI;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.JsonValue;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * This filter handles the advice AuthLevelConditionAdvice : we expect to find the advice
 * {@literal AuthLevelConditionAdvice} from the advices contained in the {@link PolicyDecisionContext} : if so, we send
 * a redirect to the correct login page wih the advice details to fulfill the conditions.
 */
class AuthLevelConditionAdviceFilter implements Filter {

    /*
     * The COMPOSITE_ADVICE index type indicates that the index name given corresponds to string in the form of XML
     * representing different Policy Authentication conditions, example AuthSchemeCondition, AuthLevelCondition, etc.
     *
     //@Checkstyle:off
     * See https://backstage.forgerock.com/static/docs/openam/13.5/apidocs/com/sun/identity/authentication/AuthContext.IndexType.html
     //@Checkstyle:on
     */
    private static final String COMPOSITE_ADVICE = "composite_advice";

    private static final String AUTH_LEVEL_ADVICE = "AuthLevelConditionAdvice";

    private final URI openamUri;
    private final String realm;

    AuthLevelConditionAdviceFilter(URI openamUri, String realm) {
        this.openamUri = openamUri;
        this.realm = realm;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        PolicyDecisionContext policyDecisionContext = context.asContext(PolicyDecisionContext.class);
        JsonValue authLevelAdvice = policyDecisionContext.getJsonAdvices().get(AUTH_LEVEL_ADVICE).defaultTo(array());
        if (authLevelAdvice.size() == 0) {
            // Give a chance to downstream advice filters or the failure handler to handle the advice.
            return next.handle(context, request);
        }
        Response response = new Response(Status.TEMPORARY_REDIRECT);
        response.getHeaders().put(new LocationHeader(buildRedirectUri(context, authLevelAdvice.get(0).asString())));
        return newResponsePromise(response);
    }

    private String buildRedirectUri(Context context, String authLevelValue) {
        final UriRouterContext routerContext = context.asContext(UriRouterContext.class);
        final URI originalUri = routerContext.getOriginalUri();
        final Form query = new Form();
        query.add("goto", originalUri.toASCIIString());
        query.add("realm", realm);
        query.add("authIndexType", COMPOSITE_ADVICE);
        query.add("authIndexValue", compositeAdvice(authLevelValue));

        return withQuery(openamUri, query).toASCIIString();
    }

    private static String compositeAdvice(String authLevelValue) {
        return format("<Advices>"
                + "<AttributeValuePair>"
                + "<Attribute name=\"%s\"/>"
                + "<Value>%s</Value>"
                + "</AttributeValuePair>"
                + "</Advices>", AUTH_LEVEL_ADVICE, authLevelValue);
    }
}

