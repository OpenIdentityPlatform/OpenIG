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

require.config({
    map: {
        "*" : {
            // TODO: Remove this when there are no longer any references to the "underscore" dependency
            "underscore": "lodash"
        }
    },
    baseUrl: "../www",
    paths: {
        jquery: "libs/jquery-2.1.1-min",
        doTimeout: "libs/jquery.ba-dotimeout-1.0-min",
        lodash: "libs/lodash-3.10.1-min",
        sinon: "libs/sinon-1.15.4"
    },
    shim: {
        sinon: {
            exports: "sinon"
        },
        doTimeout: {
            deps: ["jquery"],
            exports: "doTimeout"
        }
    }
});

require([
    "jquery",
    "org/forgerock/openig/ui/common/main/MockServer"
], function ($, MockServer) {

    $("head", document).append("<base href='../www/' />");

    require(["main", "../test/run"], function (appMain, run) {
        run(MockServer.instance);
    });

});
