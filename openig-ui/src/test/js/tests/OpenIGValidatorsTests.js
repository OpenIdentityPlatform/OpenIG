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
    "lodash",
    "i18next"
], (
    _,
    i18n
) => ({
    executeAll () {
        module("OpenIG Tests");

         // when validators invoke `callback()` without argument, that means the input is valid
        const isValid = (result) => (result === undefined);

        QUnit.asyncTest("BaseURI Validator", (assert) => {
            const moduleClass = "config/validators/OpenIGValidators";
            const validators = require(moduleClass);
            const testBaseURI = (value, callbackCheck) => {
                validators.baseURI.validator(undefined, this.fakeInputElement(value), (result) => {
                    assert.ok(
                        callbackCheck(result),
                        `URI check '${value}' with result: ${(result || "ok")}`
                    );
                });
            };

                // Valid values
            testBaseURI("http://example", (result) => (result === undefined));

            testBaseURI("http://www.example.org", (result) => (result === undefined));

            testBaseURI("http://example:8080", (result) => (result === undefined));


                //Invalid values
            testBaseURI("111", (result) => (
                _.difference(result, [i18n.t("common.form.validation.baseURINotValid")]).length === 0
            ));

            testBaseURI("http://example/subpath", (result) => (
                _.difference(result, [i18n.t("common.form.validation.baseURIContainsPath")]).length === 0
            ));

            testBaseURI("http://www.example.org/subpath", (result) => (
                _.difference(result, [i18n.t("common.form.validation.baseURIContainsPath")]).length === 0
            ));

            testBaseURI("http://www.example.org:8080/subpath", (result) => (
                _.difference(result, [i18n.t("common.form.validation.baseURIContainsPath")]).length === 0
            ));

            QUnit.start();
        });

        QUnit.asyncTest("urlCompatible Validator", (assert) => {
            const moduleClass = "config/validators/OpenIGValidators";
            const validators = require(moduleClass);
            const testURLCompatible = (value, callbackCheck) => {
                validators.urlCompatible.validator(undefined, this.fakeInputElement(value), (result) => {
                    assert.ok(
                        callbackCheck(result),
                        `URI check '${value}' with result: ${(result || "ok")}`
                    );
                });
            };

            // Valid values
            testURLCompatible("example", (result) => (result === undefined));

            testURLCompatible("example-new-id", (result) => (result === undefined));

            testURLCompatible("example-new-id123456", (result) => (result === undefined));

            testURLCompatible("123456", (result) => (result === undefined));

            //Invalid values
            testURLCompatible("bad id", (result) => (
                result && _.difference(result, [i18n.t("common.form.validation.notUrlCompatible")]).length === 0
            ));

            testURLCompatible("Bad-Id", (result) => (
                result && _.difference(result, [i18n.t("common.form.validation.notUrlCompatible")]).length === 0
            ));

            testURLCompatible("id=bad", (result) => (
                result && _.difference(result, [i18n.t("common.form.validation.notUrlCompatible")]).length === 0
            ));

            QUnit.start();
        });

        QUnit.asyncTest("customValidator Validator", (assert) => {
            const moduleClass = "config/validators/OpenIGValidators";
            const validators = require(moduleClass);
            const testCustomValidator = (value, callbackCheck) => {
                validators.customValidator.validator(
                    undefined,
                    this.fakeDataAttr("custom-valid-msg", value),
                    (result) => {
                        assert.ok(
                            callbackCheck(result),
                            `URI check '${value}' with result: ${(result || "ok")}`
                        );
                    }
                );
            };

            // Valid values
            testCustomValidator("common.form.validation.baseURINotValid",
                (result) => (
                    result && _.difference(result, [i18n.t("common.form.validation.baseURINotValid")])
                )
            );

            //Invalid values
            testCustomValidator(undefined, (result) => (result === undefined));

            QUnit.start();
        });


        QUnit.asyncTest("greaterThanOrEqualMin Validator", (assert) => {
            const moduleClass = "config/validators/OpenIGValidators";
            const validators = require(moduleClass);
            const testMinValue = (value, min, callbackCheck) => {
                validators.greaterThanOrEqualMin.validator(
                    undefined,
                    this.fakeInputElement(value, { min }),
                    (result) => {
                        assert.ok(
                            callbackCheck(result),
                            `greaterThanOrEqualMin check '${value}' >= '${min}' with result: ${(result || "ok")}`
                        );
                    }
                );
            };

            // Valid values
            testMinValue(0, 0, isValid);
            testMinValue(1, 0, isValid);
            testMinValue(5, -10, isValid);
            testMinValue(100, 10, isValid);
            testMinValue(200, 200, isValid);

            // Invalid values
            testMinValue(0, 1, (result) => (
                _.isEqual(result, [i18n.t("common.form.validation.numberGreaterThanOrEqual",
                    {
                        minAttr: 1
                    }
                )])
            ));

            testMinValue(-10, 0, (result) => (
                _.isEqual(result, [i18n.t("common.form.validation.numberGreaterThanOrEqual",
                    {
                        minAttr: 0
                    }
                )])
            ));

            QUnit.start();
        });

        QUnit.asyncTest("lessThanOrEqualMax Validator", (assert) => {
            const moduleClass = "config/validators/OpenIGValidators";
            const validators = require(moduleClass);
            const testMaxValue = (value, max, callbackCheck) => {
                validators.lessThanOrEqualMax.validator(
                    undefined,
                    this.fakeInputElement(value, { max }),
                    (result) => {
                        assert.ok(
                            callbackCheck(result),
                            `lessThanOrEqualMax check '${value}' <= '${max}' with result: ${(result || "ok")}`
                        );
                    }
                );
            };

            // Valid values
            testMaxValue(0, 1, isValid);
            testMaxValue(-5, 1, isValid);
            testMaxValue(9000, 65000, isValid);
            testMaxValue(100, 100, isValid);

            // Invalid values
            testMaxValue(5, 1, (result) => (
                _.isEqual(result, [i18n.t("common.form.validation.numberLessThanOrEqual",
                    {
                        maxAttr: 1
                    }
                )])
            ));

            testMaxValue(1, -1, (result) => (
                _.isEqual(result, [i18n.t("common.form.validation.numberLessThanOrEqual",
                    {
                        maxAttr: -1
                    }
                )])
            ));

            QUnit.start();
        });
    },

    // Create object with val() method to fake real input
    fakeInputElement (value, htmlAttrs) {
        return {
            val () {
                return value;
            },
            attr (name) {
                return htmlAttrs[name];
            }
        };
    },

    fakeDataAttr (dataAttr, value) {
        return {
            data (attr) {
                if (attr === dataAttr) {
                    return value;
                }
                return;
            }
        };
    }
})
);
