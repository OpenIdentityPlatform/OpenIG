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

package org.forgerock.openig.util;

import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Bindings;
import org.forgerock.util.Function;

class BindingsFromJsonValueFunction implements Function<JsonValue, Bindings, JsonValueException> {

    @Override
    public Bindings apply(JsonValue value) {
        Bindings bindings = Bindings.bindings();
        for (String key : value.expect(Map.class).keys()) {
            bindings.bind(key, value.get(key).getObject());
        }
        return bindings;
    }
}

