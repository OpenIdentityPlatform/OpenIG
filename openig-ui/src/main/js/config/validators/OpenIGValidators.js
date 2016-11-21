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
    "i18next"
], (
    i18n
) => ({
    "baseURI": {
        "name": "Base URI field",
        "dependencies": [],
        "validator" (el, input, callback) {
            const v = input.val();
            if (!/^(http|https):\/\/[^ "]+$/.test(v)) {
                callback([i18n.t("common.form.validation.baseURINotValid")]);
            } else if (!/^(http|https):\/\/[^ "\/]+$/.test(v)) {
                callback([i18n.t("common.form.validation.baseURIContainsPath")]);
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
                callback([i18n.t("common.form.validation.URINotValid")]);
                return;
            }
            callback();
        }
    },
    "greaterThanOrEqualMin": {
        "name": "Greater than or equal min field value",
        "dependencies": [],
        "validator" (el, input, callback) {
            const min = input.attr("min");
            if (min <= input.val()) {
                callback();
                return;
            }
            callback([i18n.t("common.form.validation.numberGreaterThanOrEqual", {
                minAttr: min
            })]);
        }
    },
    "lessThanOrEqualMax": {
        "name": "Less than or equal max field value",
        "dependencies": [],
        "validator" (el, input, callback) {
            const max = input.attr("max");
            if (Number(max) >= input.val()) {
                callback();
                return;
            }
            callback([i18n.t("common.form.validation.numberLessThanOrEqual", {
                maxAttr: max
            })]);
        }
    },
    "urlCompatible": {
        "name": "Url compatible value",
        "dependencies": [],
        "validator" (el, input, callback) {
            const v = input.val();
            if (/^[\d,a-z,-]+$/.test(v)) {
                callback();
            } else {
                callback([i18n.t("common.form.validation.notUrlCompatible")]);
            }
        }
    },
    "customValidator": {
        "name": "Custom validator",
        "dependencies": [],
        "validator" (el, input, callback) {
            const validMsg = input.data("custom-valid-msg");
            if (validMsg) {
                callback([i18n.t(validMsg)]);
                return;
            }
            callback();
        }
    }
}));
