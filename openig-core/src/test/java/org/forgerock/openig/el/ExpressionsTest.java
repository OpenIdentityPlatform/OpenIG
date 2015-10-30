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

package org.forgerock.openig.el;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.el.Expressions.evaluate;

import java.util.Map;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ExpressionsTest {

    @Test
    public void shouldEvaluateBasicStringConfiguration() throws Exception {
        Map<String, Object> args = json(object(field("ultimateAnswer", "${5*8+2}"))).asMap();

        final Map<String, Object> evaluated = evaluate(args, bindings());
        assertThat(evaluated).containsOnly(entry("ultimateAnswer", 42L));
    }

    @Test
    public void shouldEvaluateArrayConfiguration() throws Exception {
        Map<String, Object> args = json(object(field("ultimateAnswer",
                                                    array("${5*8+2}",
                                                          1515,
                                                          "${decodeBase64('Rm9yZ2VSb2Nr')}")))).asMap();

        final Map<String, Object> evaluated = evaluate(args, bindings());
        assertThat(evaluated).containsOnly(entry("ultimateAnswer", asList(42L, 1515, "ForgeRock")));
    }

    @Test
    public void shouldEvaluateObjectConfiguration() throws Exception {
        Map<String, Object> args;
        args = json(object(field("ultimateAnswer",
                                object(field("rockstar", "${decodeBase64('Rm9yZ2VSb2Nr')}"))))).asMap();

        final Map<String, Object> evaluated = evaluate(args, bindings());
        assertThat(evaluated).containsOnly(entry("ultimateAnswer", singletonMap("rockstar", "ForgeRock")));
    }
}
