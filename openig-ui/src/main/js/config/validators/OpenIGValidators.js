/**
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

define([
    "jquery"
], (
    $
) => ({
    "baseURI": {
        "name": "Base URI field",
        "dependencies": [],
        "validator" (el, input, callback) {
            const v = input.val();
            if (!/^(http|https):\/\/[^ "]+$/.test(v)) {
                callback([$.t("common.form.validation.baseURINotValid")]);
            } else if (!/^(http|https):\/\/[^ "\/]+$/.test(v)) {
                callback([$.t("common.form.validation.baseURIContainsPath")]);
            } else {
                callback();
            }
        }
    },
    "uri": {
        "name": "URI field",
        "dependencies": [],
        "validator" (el, input, callback) {
            const v = input.val();
            if (v.length > 0 && !/^(http|https):\/\/[^ "]+$/.test(v)) {
                callback([$.t("common.form.validation.URINotValid")]);
                return;
            }
            callback();
        }
    },
    "greaterThanOrEqualMin": {
        "name": "Greater than or equal min field value",
        "dependencies": [],
        "validator" (el, input, callback) {
            const min = input[0].min;
            if (min <= input.val()) {
                callback();
                return;
            }
            callback([$.t("common.form.validation.numberGreaterThanOrEqual", {
                minAttr: min
            })]);
        }
    }
}));
