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

/**
 * Fake server to handle AJAX requests.
 *
 * @author Eugenia Sergueeva
 */

define([
    "mock/Data",
    "sinon"
], (mockData, sinon) => {
    var instance = null,
        server;

    function init () {

        sinon.FakeXMLHttpRequest.useFilters = true;
        sinon.FakeXMLHttpRequest.addFilter((method, url) => {
            // Change to true for using mock data
            const useMockData = false;
            if (!useMockData) {
                return true;
            }
            return (/((\.html)|(\.css)|(\.less)|(\.json))$/).test(url);
        });

        server = sinon.fakeServer.create();
        server.autoRespond = true;

        mockData(server);
        return server;
    }

    return {
        instance: (() => {
            if (!instance) {
                instance = init();
            }

            return instance;
        })()
    };
});
