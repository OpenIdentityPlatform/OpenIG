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
 * Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.resource.core.routing;

import static org.forgerock.resource.core.ResourceName.urlDecode;
import static org.forgerock.resource.core.routing.RoutingMode.EQUALS;
import static org.forgerock.resource.core.routing.RoutingMode.STARTS_WITH;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.resource.core.Context;

/**
 * An opaque handle for a route which has been registered in a {@link AbstractUriRouter
 * router}. A reference to a route should be maintained if there is a chance
 * that the route will need to be removed from the router at a later time.
 *
 * @see AbstractUriRouter
 *
 * @param <H> The type of the handler that will be used to handle routing requests.
 *
 * @since 1.0.0
 */
public final class UriRoute<H> {

    private final H handler;
    private final RoutingMode mode;
    private final Pattern regex;
    private final String uriTemplate;
    private final List<String> variables = new LinkedList<String>();

    UriRoute(RoutingMode mode, String uriTemplate, H handler) {
        this.handler = handler;
        String t = removeTrailingSlash(removeLeadingSlash(uriTemplate));
        StringBuilder builder = new StringBuilder(t.length() + 8);

        // Parse the template.
        boolean isInVariable = false;
        int elementStart = 0;
        builder.append('('); // Group 1 does not include trailing portion for STARTS_WITH.
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (isInVariable) {
                if (c == '}') {
                    if (elementStart == i) {
                        throw new IllegalArgumentException("URI template " + t
                                + " contains zero-length template variable");
                    }
                    variables.add(t.substring(elementStart, i));
                    builder.append("([^/]+)");
                    isInVariable = false;
                    elementStart = i + 1;
                } else if (!isValidVariableCharacter(c)) {
                    throw new IllegalArgumentException("URI template " + t
                            + " contains an illegal character " + c + " in a template variable");
                } else {
                    // Continue counting characters in variable.
                }
            } else if (c == '{') {
                // Escape and add literal substring.
                builder.append(Pattern.quote(t.substring(elementStart, i)));
                isInVariable = true;
                elementStart = i + 1;
            }
        }

        if (isInVariable) {
            throw new IllegalArgumentException("URI template " + t
                    + " contains a trailing unclosed variable");
        }

        // Escape and add remaining literal substring.
        builder.append(Pattern.quote(t.substring(elementStart)));
        builder.append(')');

        if (mode == STARTS_WITH) {
            // Add wild-card match for remaining unmatched path.
            if (uriTemplate.isEmpty()) {
                /*
                 * Special case for empty template: the next path element is
                 * not preceded by a slash. The redundant parentheses are
                 * required in order to have consistent group numbering with
                 * the non-empty case.
                 */
                builder.append("((.*))?");
            } else {
                builder.append("(/(.*))?");
            }
        }

        this.uriTemplate = uriTemplate;
        this.mode = mode;
        this.regex = Pattern.compile(builder.toString());
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (mode == EQUALS) {
            builder.append("equals(");
        } else {
            builder.append("startsWith(");
        }
        builder.append(uriTemplate);
        builder.append(')');
        return builder.toString();
    }

    RouteMatcher<H> getRouteMatcher(Context context, String uri) {
        Matcher matcher = regex.matcher(uri);
        if (!matcher.matches()) {
            return null;
        }
        Map<String, String> variableMap;
        switch (variables.size()) {
            case 0:
                variableMap = Collections.emptyMap();
                break;
            case 1:
                // Group 0 matches entire URL, group 1 matches entire template.
                variableMap = Collections.singletonMap(variables.get(0), urlDecode(matcher.group(2)));
                break;
            default:
                variableMap = new LinkedHashMap<String, String>(variables.size());
                for (int i = 0; i < variables.size(); i++) {
                    // Group 0 matches entire URL, group 1 matches entire template.
                    variableMap.put(variables.get(i), urlDecode(matcher.group(i + 2)));
                }
                break;
        }
        String remaining = removeLeadingSlash(uri.substring(matcher.end(1)));
        String matched = matcher.group(1);
        return new RouteMatcher<H>(this, matcher.group(1), remaining, new RouterContext(context,
                matched, variableMap), handler);
    }

    RoutingMode getMode() {
        return mode;
    }

    private String removeLeadingSlash(String resourceName) {
        if (resourceName.startsWith("/")) {
            return resourceName.substring(1);
        }
        return resourceName;
    }

    private String removeTrailingSlash(String resourceName) {
        if (resourceName.endsWith("/")) {
            return resourceName.substring(0, resourceName.length() - 1);
        }
        return resourceName;
    }

    // As per RFC.
    private boolean isValidVariableCharacter(char c) {
        return ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'))
                || ((c >= '0') && (c <= '9')) || (c == '_');
    }
}
