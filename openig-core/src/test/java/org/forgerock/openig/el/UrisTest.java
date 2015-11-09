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
import static org.forgerock.openig.el.Bindings.bindings;

import java.util.HashMap;
import java.util.Map;

import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class UrisTest {

    private Map<String, Object> attributes;
    private Request request;
    private Bindings bindings;

    @BeforeMethod
    public void beforeMethod() {
        attributes = new HashMap<>();
        request = new Request();
        bindings = bindings().bind("attributes", attributes).bind("request", request);
    }

    @DataProvider
    private Object[][] notSupportedMethods() {
        return new Object[][] {
            { "${create(\"a\", \"b\", \"c\", 4, \"/e%3D\", \"x=%3D\", \"g%3D\")}" },
            { "${createNonStrict(\"http\", null, \"localhost\", 8081, \"raw/path\", \"param1=w s\", \"g%3D\")}" },
            { "${rebase(request.uri, \"http://www.example.com/\")}" },
            { "${withQuery(request.uri, form)}" },
            { "${withoutQueryAndFragment(request.uri)}" },
        };
    }

    @Test
    public void shouldFormDecodeParameterNameOrValue() throws Exception {
        request.setUri("http%3A%2F%2Fwww.example.org%2Fform%3Ftext%3DHello%20World");
        String o = Expression.valueOf("${formDecodeParameterNameOrValue(request.uri)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("http://www.example.org/form?text=Hello World");
    }

    @Test
    public void shouldFormEncodeParameterNameOrValue() throws Exception {
        request.setUri("http://www.example.org/form?text=Hello+World");
        String o = Expression.valueOf("${formEncodeParameterNameOrValue(request.uri)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("http%3A%2F%2Fwww.example.org%2Fform%3Ftext%3DHello%2BWorld");
    }

    @Test
    public void shouldUrlDecodeFragment() throws Exception {
        attributes.put("fragment", "#search=\"word1/word2\"");
        String o = Expression.valueOf("${urlDecodeFragment(attributes.fragment)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("#search=\"word1/word2\"");
    }

    @Test
    public void shouldUrlEncodeFragment() throws Exception {
        attributes.put("fragment", "#search=\"cave canem\"&chap/1");
        String o = Expression.valueOf("${urlEncodeFragment(attributes.fragment)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("%23search=%22cave%20canem%22&chap/1");
    }

    @Test
    public void shouldUrlDecodePathElement() throws Exception {
        attributes.put("path", "over%2FthereazAZ09-._~%2F");
        String o = Expression.valueOf("${urlDecodePathElement(attributes.path)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("over/thereazAZ09-._~/");
    }

    @Test
    public void shouldUrlEncodePathElement() throws Exception {
        attributes.put("path", "over/thereazAZ09-._~/");
        String o = Expression.valueOf("${urlEncodePathElement(attributes.path)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("over%2FthereazAZ09-._~%2F");
    }

    @Test
    public void shouldUrlDecodeQueryParameterNameOrValue() throws Exception {
        attributes.put("query", "?name%3Dhelmet_the_ferret");
        String o = Expression.valueOf("${urlDecodeQueryParameterNameOrValue(attributes.query)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("?name=helmet_the_ferret");
    }

    @Test
    public void shouldUrlEncodeQueryParameterNameOrValue() throws Exception {
        attributes.put("query", "?name=helmet_the_ferret");
        String o = Expression.valueOf("${urlEncodeQueryParameterNameOrValue(attributes.query)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("?name%3Dhelmet_the_ferret");
    }

    @Test
    public void shouldUrlEncodeUserInfo() throws Exception {
        attributes.put("query", "helmet+the+ferret@example.org");
        String o = Expression.valueOf("${urlEncodeUserInfo(attributes.query)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("helmet+the+ferret%40example.org");
    }

    @Test
    public void shouldUrlDecodeUserInfo() throws Exception {
        attributes.put("userInfo", "helmet+the+ferret%40example.org");
        String o = Expression.valueOf("${urlDecodeUserInfo(attributes.userInfo)}",
                                      String.class).eval(bindings);
        assertThat(o).isEqualTo("helmet+the+ferret@example.org");
    }

    @Test(dataProvider = "notSupportedMethods",
          expectedExceptions = ExpressionException.class,
          expectedExceptionsMessageRegExp = ".*Could not resolve function.*")
    public void shouldFailWithUnssupportedMethods(final String expression) throws Exception {
        // Given
        Form form = new Form();
        form.add("goto", "http://some.url");
        bindings = bindings().bind("attributes", attributes).bind("form", form);
        request.setUri("https://doot.doot.doo.org/all/good/things?come=to&those=who#breakdance");

        // When
        Expression.valueOf(expression, String.class).eval(bindings);
    }
}
