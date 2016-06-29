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
    "jquery",
    "underscore"
], function (
    $,
    _
) {
    return {
        executeAll: function () {
            module("OpenIG Tests");

            QUnit.asyncTest("BaseURI Validator", _.bind(function (assert) {
                var moduleClass = "config/validators/OpenIGValidators",
                    validators = require(moduleClass),
                    testBaseURI = _.bind(function (value, callbackCheck) {
                        validators.baseURI.validator(undefined, this.fakeInputElement(value), function (result) {
                            assert.ok(
                                callbackCheck(result),
                                "URI check '" + value + "' with result: " + (result || "ok")
                            );
                        });
                    }, this);


                // Valid values
                testBaseURI("http://example", function (result) { return result === undefined; });

                testBaseURI("http://www.example.org", function (result) { return result === undefined; });

                testBaseURI("http://example:8080", function (result) { return result === undefined; });


                //Invalid values
                testBaseURI("111", function (result) {
                    return _.difference(result, [$.t("common.form.validation.baseURINotValid")]).length === 0;
                });

                testBaseURI("http://example/subpath", function (result) {
                    return _.difference(result, [$.t("common.form.validation.baseURIContainsPath")]).length === 0;
                });

                testBaseURI("http://www.example.org/subpath", function (result) {
                    return _.difference(result, [$.t("common.form.validation.baseURIContainsPath")]).length === 0;
                });

                testBaseURI("http://www.example.org:8080/subpath", function (result) {
                    return _.difference(result, [$.t("common.form.validation.baseURIContainsPath")]).length === 0;
                });

                QUnit.start();
            }, this));
        },
        // Create object with val() method to fake real input
        fakeInputElement: function (value) {
            return {
                val: function () {
                    return value;
                }
            };
        }
    };
});
