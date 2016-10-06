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
    "org/forgerock/openig/ui/admin/util/DataFilter"
], (
    DataFilter
) => ({
    executeAll () {
        module("DataFilter Tests");

        QUnit.asyncTest("DataFilter", (assert) => {
            const data = {
                id: "exampleapp",
                name: "Example app 1",
                uri: "http://app.example.com:8080",
                status: "deployed"
            };

            const testDataFilter = (filter, expectedResult) => {
                const dataFilter = new DataFilter(filter, ["id", "uri", "name", "status"]);
                assert.ok(
                    dataFilter.filter(data) === expectedResult,
                    `Filter: ${filter}`
                );
            };

            // Catch-all search match values containing provided term
            testDataFilter("http", true); // begin
            testDataFilter("ploy", true); // middle
            testDataFilter("80", true);   // end

            // Catch-all fail to find match for missing term in all values
            testDataFilter("missing", false);

            // Case is ignored
            testDataFilter("APP", true);
            testDataFilter("id:EXAM", true);

            // Value are trimmed
            testDataFilter(" amp ", true);
            testDataFilter(" id:examp ", true);

            // "Namespaced" search opt-in values that starts with provided term
            testDataFilter("id:exa", true);
            testDataFilter("status:undepl", false);

            // Wrong namespace doesn't match anything
            testDataFilter("missing:value", false);

            // Multiple terms are ANDed
            testDataFilter("http app", true);
            testDataFilter("missing app", false);

            // Multiple same namespace
            testDataFilter("id:example id:example", true);
            testDataFilter("id:ex app 1 id:example", true);
            testDataFilter("id:not id:example", false);
            testDataFilter("id:example id:not", false);

            // Values with ":"
            testDataFilter(":8080", true);
            testDataFilter("app :8080", true);
            testDataFilter("app :80 id:example", true);
            testDataFilter("uri:http://app.ex", true);
            testDataFilter("uri:http://:app.ex", false);
            testDataFilter("id:exa uri:http://app.ex", true);

            QUnit.start();
        });
    }
})
);
