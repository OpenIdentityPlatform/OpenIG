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
    "org/forgerock/commons/ui/common/main/Configuration",
    "org/forgerock/openig/ui/common/main/LocalStorage"
], () => (
    (server) => {
        server.respondWith(
            "GET",
            "/openig/api/system/objects/ui/record",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify({
                    "result": [
                        {
                            "_id": "454545legacyapp",
                            "_rev": "1",
                            "info": {
                                "created_at": 1234567,
                                "version": "5.0.0-SNAPSHOT"
                            },

                            "id": "legacy-web-app",
                            "name": "Legacy Web App",
                            "baseURI": "http://www.legacyapp.com:8080",
                            "condition": "${request.uri.path == '/legacy'}",
                            "router": "",
                            "route": "",
                             // Order is as defined by user in "chain"
                            "filters": []
                        },
                        {
                            "_id": "797979weatherapi",
                            "_rev": "1",
                            "info": {
                                "created_at": 1234567,
                                "version": "5.0.0-SNAPSHOT"
                            },
                            "id": "weatherapi",
                            "name": "weatherAPI",
                            "baseURI": "http://www.weather.com:8081",
                            "condition": "${request.uri.path == '/weather'}",
                            "deployedDate": new Date(),
                            "pendingChanges": false
                        }
                    ],
                    "resultCount": 2,
                    "pagedResultsCookie": null,
                    "remainingPagedResults": -1
                })
            ]
        );

        server.respondWith(
            "POST",
            "/openig/api/system/objects/ui/record/454545legacyapp",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify([
                    {
                        "id": "legacy-web-app",
                        "name": "Legacy Web App",
                        "baseURI": "http://www.legacyapp.com:8080",
                        "condition": "${request.uri.path == '/legacy'}",
                        "status": "undeployed"
                    }
                ])
            ]
        );

        server.respondWith(
            "POST",
            "/openig/api/system/objects/ui/record/797979weatherapi",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify([
                    {
                        "id": "weatherapi",
                        "name": "weatherAPI",
                        "baseURI": "http://www.weather.com:8081",
                        "condition": "${request.uri.path == '/weather'}",
                        "status": "deployed"
                    }
                ])
            ]
        );

        server.respondWith(
            "DELETE",
            "/openig/api/system/objects/ui/record/454545legacyapp?",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify([
                    {
                        "id": "legacy-web-app",
                        "name": "Legacy Web App",
                        "baseURI": "http://www.legacyapp.com:8080",
                        "condition": "${matches(request.uri.path, '^/legacy')}",
                        "status": "undeployed"
                    }
                ])
            ]
        );

        server.respondWith(
            "GET",
            "/openig/api/system/objects/_router/routes?_pageSize=10",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify({
                    "result": [
                        {
                            "_id": "weatherapi", // name
                            "_rev": "1",
                            "uptime": 513246, // ms
                            "condition": "${request.uri.path == '/weather'}"

                        },
                        {
                            "_id": "legacy-web-app", // name
                            "_rev": "1",
                            "uptime": 513246, // ms
                            "condition": "${request.uri.path == '/legacy'}"
                        }

                    ],
                    "resultCount": 2,
                    "pagedResultsCookie": null,
                    "remainingPagedResults": -1
                })
            ]
        );

        server.respondWith(
            "PUT",
            "/openig/api/system/objects/_router/routes/legacy-web-app",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify({
                    "_id": "legacy-web-app"
                })
            ]
        );

        server.respondWith(
            "DELETE",
            "/openig/api/system/objects/_router/routes/legacy-web-app?",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify({
                    "_id": "legacy-web-app"
                })
            ]
        );

        server.respondWith(
            "PUT",
            "/openig/api/system/objects/_router/routes/weatherapi?",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify({
                    "_id": "weatherapi"
                })
            ]
        );

    }
));
