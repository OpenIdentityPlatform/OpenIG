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
 * Portions copyright 2025 3A Systems LLC.
 */

package org.forgerock.openig.filter.oauth2;

import org.forgerock.http.Handler;
import org.forgerock.http.oauth2.AccessTokenInfo;
import org.forgerock.http.oauth2.OAuth2Context;
import org.forgerock.http.oauth2.ResourceServerFilter;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.filter.oauth2.client.HeapUtilsTest;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Keys;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.time.TimeService;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.script.Script.GROOVY_MIME_TYPE;
import static org.mockito.Mockito.mock;

@SuppressWarnings("javadoc")
public class OAuth2ResourceServerFilterHeapletTest {

    /**
     * Re-used token-id.
     */
    public static final String TOKEN_ID = "1fc0e143-f248-4e50-9c13-1d710360cec9";


    @Test
    public void shouldEvaluateScopeExpressions() throws Exception {
        final AttributesContext context = new AttributesContext(new RootContext());
        context.getAttributes().put("attribute", "a");
        Request request = buildAuthorizedRequest();
        final OAuth2ResourceServerFilterHeaplet.OpenIGResourceAccess resourceAccess =
                new OAuth2ResourceServerFilterHeaplet.OpenIGResourceAccess(getScopes("${attributes.attribute}",
                                                                              "${split('to,b,or,not,to', ',')[1]}",
                                                                              "c"));
        assertThat(resourceAccess.getRequiredScopes(context, request)).containsOnly("a", "b", "c");
    }

    @Test(expectedExceptions = ResponseException.class,
          expectedExceptionsMessageRegExp = ".*scope expression \'.*\' could not be resolved")
    public void shouldFailDueToInvalidScopeExpressions() throws Exception {
        final OAuth2ResourceServerFilterHeaplet.OpenIGResourceAccess resourceAccess =
                new OAuth2ResourceServerFilterHeaplet.OpenIGResourceAccess(getScopes("${bad.attribute}"));
        resourceAccess.getRequiredScopes(newContextChain(), buildAuthorizedRequest());
    }

    @Test
    public void testFilterWithScriptableAccessTokenResolver() throws Exception {
        final String TEST_TOKEN = UUID.randomUUID().toString();

        HeapImpl heap = HeapUtilsTest.buildDefaultHeap();

        heap.put(Keys.ENVIRONMENT_HEAP_KEY, getEnvironment());
        heap.put(Keys.CLIENT_HANDLER_HEAP_KEY, mock(Handler.class));
        heap.put(Keys.TIME_SERVICE_HEAP_KEY, TimeService.SYSTEM);

        JsonValue config = json(object());
        config.put("providerHandler", "ClientHandler");
        List<String> scopes = new ArrayList<>();
        config.put("scopes", scopes);
        config.put("cacheExpiration", "0 minutes");
        config.put("requireHttps", false);

        Map<String, Object> accessTokenResolver = new HashMap<>();
        accessTokenResolver.put("type", "ScriptableAccessTokenResolver");
        Map<String, Object> accessTokenResolverConfig = new HashMap<>();
        accessTokenResolverConfig.put("type", GROOVY_MIME_TYPE);
        accessTokenResolverConfig.put("file", "AccessTokenResolver.groovy");
        accessTokenResolver.put("config", accessTokenResolverConfig);
        config.put("accessTokenResolver", accessTokenResolver);

        ResourceServerFilter filter = (ResourceServerFilter) new OAuth2ResourceServerFilterHeaplet().create(Name.of("this"), config, heap);

        Handler testHandler = (context, request) -> {
            assertThat(context).isInstanceOf(OAuth2Context.class);
            AccessTokenInfo accessToken = ((OAuth2Context) context).getAccessToken();
            assertThat(accessToken.getToken()).isEqualTo(TEST_TOKEN);
            assertThat(accessToken.getInfo().get("name")).isEqualTo("John");
            return null;
        };

        Request testRequest = new Request();
        testRequest.getHeaders().add("Authorization", "Bearer " + TEST_TOKEN);
        Context ctx = new RootContext();
        filter.filter(ctx, testRequest, testHandler);
    }

    private Environment getEnvironment() throws Exception {
        return new DefaultEnvironment(new File(getTestBaseDirectory()));
    }

    private String getTestBaseDirectory() throws Exception {
        // relative path to our-self
        String name = resource(getClass());
        // find the complete URL pointing to our path
        URL resource = getClass().getClassLoader().getResource(name);

        // Strip out the 'file' scheme
        String path = new File(resource.toURI()).getPath();

        // Strip out the resource path to actually get the base directory
        return path.substring(0, path.length() - name.length());
    }

    private static String resource(final Class<?> type) {
        return type.getName().replace('.', '/').concat(".class");
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
