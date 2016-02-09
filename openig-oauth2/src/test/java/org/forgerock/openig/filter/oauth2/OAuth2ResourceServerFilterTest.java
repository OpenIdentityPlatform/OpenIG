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

package org.forgerock.openig.filter.oauth2;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OAuth2ResourceServerFilterTest {

    /**
     * Re-used token-id.
     */
    public static final String TOKEN_ID = "1fc0e143-f248-4e50-9c13-1d710360cec9";

    @Test
    public void shouldEvaluateScopeExpressions() throws Exception {
        final AttributesContext context = new AttributesContext(new RootContext());
        context.getAttributes().put("attribute", "a");
        Request request = buildAuthorizedRequest();
        final OAuth2ResourceServerFilter.OpenIGResourceAccess resourceAccess =
                new OAuth2ResourceServerFilter.OpenIGResourceAccess(getScopes("${attributes.attribute}",
                                                                              "${split('to,b,or,not,to', ',')[1]}",
                                                                              "c"));
        assertThat(resourceAccess.getRequiredScopes(context, request)).containsOnly("a", "b", "c");
    }

    @Test(expectedExceptions = ResponseException.class,
          expectedExceptionsMessageRegExp = ".*scope expression \'.*\' could not be resolved")
    public void shouldFailDueToInvalidScopeExpressions() throws Exception {
        final OAuth2ResourceServerFilter.OpenIGResourceAccess resourceAccess =
                new OAuth2ResourceServerFilter.OpenIGResourceAccess(getScopes("${bad.attribute}"));
        resourceAccess.getRequiredScopes(newContextChain(), buildAuthorizedRequest());
    }

    private static Context newContextChain() {
        return new AttributesContext(new RootContext());
    }

    private static Set<Expression<String>> getScopes(final String... scopes) throws ExpressionException {
        final Set<Expression<String>> expScopes = new HashSet<>(scopes.length);
        for (final String scope : scopes) {
            expScopes.add(Expression.valueOf(scope, String.class));
        }
        return expScopes;
    }

    private static Request buildAuthorizedRequest() {
        Request request = new Request();
        request.getHeaders().add("Authorization", format("Bearer %s", TOKEN_ID));
        return request;
    }
}
