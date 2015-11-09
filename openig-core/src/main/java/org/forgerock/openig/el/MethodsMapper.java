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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.el;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.el.FunctionMapper;

import org.forgerock.http.util.Uris;
import org.forgerock.util.Reject;
import org.forgerock.util.annotations.VisibleForTesting;

/**
 * This class maps methods between EL and OpenIG.
 */
final class MethodsMapper extends FunctionMapper {

    public static final MethodsMapper INSTANCE;
    static {
        final Map<String, Method> methods = new HashMap<>();
        methods.putAll(getPublicStaticMethods(Functions.class));
        methods.putAll(filterByName(getPublicStaticMethods(Uris.class),
                                    Pattern.compile(".*(Encode|Decode).*")));
        INSTANCE = new MethodsMapper(methods);
    }

    final private Map<String, Method> methods = new HashMap<>();

    @VisibleForTesting
    MethodsMapper(final Map<String, Method> methods) {
        Reject.ifNull(methods);
        this.methods.putAll(methods);
    }

    @Override
    public Method resolveFunction(String prefix, String localName) {
        if (prefix != null && prefix.isEmpty() && localName != null) {
            return methods.get(localName);
        }
        // no match was found
        return null;
    }

    static Map<String, Method> getPublicStaticMethods(final Class<?> target) {
        final Map<String, Method> methods = new HashMap<>();
        for (final Method method : target.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                methods.put(method.getName(), method);
            }
        }
        return methods;
    }

    static Map<String, Method> filterByName(final Map<String, Method> given,
                                            final Pattern pattern) {
        final Map<String, Method> methods = new HashMap<>();
        for (final Method method : given.values()) {
            if (pattern.matcher(method.getName()).matches()) {
                methods.put(method.getName(), method);
            }
        }
        return methods;
    }
}
