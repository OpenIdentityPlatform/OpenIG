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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.uma;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
import static org.forgerock.util.Reject.checkNotNull;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.forgerock.http.protocol.Request;
import org.forgerock.openig.el.Expression;

/**
 * A {@link ShareTemplate} statically defines, for a given resource path pattern, the exhaustive list of
 * scopes that may be required for request access control.
 *
 * <p>The {@code actions} list declares the set of scopes to ask for depending on the incoming request.
 * All {@code scopes} are collected at initialization time to produce the exhaustive list of scopes supported
 * by protected resources.
 *
 * <p>The {@code condition} expressions can use the {@literal request} variable that is the incoming requesting party
 * {@link Request}.
 *
 * <pre>
 *     {@code {
 *         "pattern": "/allergies/.*",
 *         "actions" : [
 *           {
 *             "scopes"    : [ "http://api.example.com/operations#read" ],
 *             "condition" : "${request.method == 'GET'}"
 *           },
 *           {
 *             "scopes"    : [ "http://api.example.com/operations#delete" ],
 *             "condition" : "${request.method == 'DELETE'}"
 *           }
 *         ]
 *       }
 *     }
 * </pre>
 */
class ShareTemplate {
    private final Pattern pattern;
    private final List<Action> actions;
    private final Set<String> scopes = new TreeSet<>();

    ShareTemplate(final Pattern pattern, final List<Action> actions) {
        this.pattern = checkNotNull(pattern);
        this.actions = checkNotNull(actions);
        for (Action action : actions) {
            scopes.addAll(action.getScopes());
        }
    }

    public Pattern getPattern() {
        return pattern;
    }

    public Set<String> getScopes(final Request request) {
        for (Action action : actions) {
            if (action.accept(request)) {
                return action.getScopes();
            }
        }
        return emptySet();
    }

    public Set<String> getAllScopes() {
        return scopes;
    }

    public static class Action {
        private final Expression<Boolean> condition;
        private final Set<String> scopes;

        public Action(final Expression<Boolean> condition, final Set<String> scopes) {
            this.condition = condition;
            this.scopes = scopes;
        }

        public Set<String> getScopes() {
            return scopes;
        }

        public boolean accept(Request request) {
            return condition.eval(singletonMap("request", request));
        }
    }
}
