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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.el.MethodsMapper.filterByName;
import static org.forgerock.openig.el.MethodsMapper.getPublicStaticMethods;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MethodsMapperTest {

    private Map<String, Method> methods;

    @BeforeMethod
    public void beforeMethod() {
        methods = new HashMap<>(getPublicStaticMethods(SampleClass.class));
    }

    @DataProvider
    private Object[][] resolveFunctionUnsupportedParameters() {
        return new Object[][] {
            { null, "asLong" },
            { "noPrefixAllowed", "" },
            { "", null }
        };
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWhenNullMethodsProvided() {
        new MethodsMapper(null);
    }

    @Test(dataProvider = "resolveFunctionUnsupportedParameters")
    public void shouldResolveFunctionReturnNull(final String prefix, final String methodName) {
        assertThat(new MethodsMapper(methods).resolveFunction(prefix, methodName)).isNull();
    }

    @Test
    public void shouldResolveFunctionWithEmptyPrefix() {
        MethodsMapper mp = new MethodsMapper(methods);
        assertThat(mp.resolveFunction("", "asLong")).isEqualTo(methods.get("asLong"));
        assertThat(mp.resolveFunction("", "urlEncodeFragment")).isEqualTo(null);
        mp = MethodsMapper.INSTANCE;
        assertThat(mp.resolveFunction("", "asLong")).isNull();
        assertThat(mp.resolveFunction("", "urlEncodeFragment")).isNotNull();
    }

    @Test
    public void shouldGetPublicMethods() {
        assertThat(getPublicStaticMethods(SampleClass.class)).hasSize(3)
                                                             .containsKeys("asUri", "asLong", "asFoo");
    }

    @Test
    public void shouldFilterMethods() {
        assertThat(filterByName(methods, Pattern.compile(".*asU.*"))).hasSize(1);
        assertThat(filterByName(methods, Pattern.compile(".*as.*"))).hasSize(3);
    }

    static class SampleClass {
        private String value;

        public static URI asUri(final String value) throws URISyntaxException {
            return new URI(value);
        }

        public static Long asLong(final String value) throws NumberFormatException {
            return Long.parseLong(value);
        }

        public static String asFoo(final String value) {
            return "Foo=" + value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }
    }
}
