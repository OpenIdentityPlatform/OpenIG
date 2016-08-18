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
            "/openig/api/system/objects/config?_pageSize=10",
            //not matching params "/openig/api/system/objects/config"
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify({
                    "result": [
                        {
                            "_id": "legacyapp",
                            "_rev": "1",
                            "info": {
                                "created_at": 1234567,
                                "version": "5.0.0-SNAPSHOT"
                            },
                            "content": {
                                "id": "legacyapp",
                                "name": "Legacy Web App",
                                "url": "http://www.legacyapp.com:8080",
                                "condition": "/lego",
                                "router": "",
                                "route": "",

                                // Order is as defined by user in "chain"
                                "filters": [
                                    {
                                        //Throttling
                                        "enabled": true,
                                        "type": "ThrottlingFilter",
                                        "numberOfRequests": 60,
                                        "duration": "1 minute"
                                    },
                                    {
                                        //Authentication
                                        "enabled": true,
                                        "type": "OAuth2ClientFilter",
                                        "clientEndpoint": "/openid",
                                        "loginUri": "",
                                        "logoutUri": "",
                                        "metadata": ""
                                    },
                                    // Password Replay
                                    {
                                        "enabled": false,
                                        "type": "PasswordReplayFilter",
                                        "request": {
                                            "uri": "",
                                            "method": "",
                                            "form": {},
                                            "headers": {}
                                        },
                                        "loginPage": "",
                                        "credentials": ""
                                    }
                                ]


                            }
                        },
                        {
                            "_id": "weatherapi",
                            "_rev": "1",
                            "info": {
                                "created_at": 1234567,
                                "version": "5.0.0-SNAPSHOT"
                            },
                            "content": {
                                "name": "weatherAPI",
                                "url": "http://www.weather.com:8081",
                                "condition": "",
                                "deployedDate": new Date(),
                                "pendingChanges": false
                            }
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
            "/openig/api/system/objects/config/legacyapp",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify([
                    {
                        "name": "Legacy Web App2",
                        "url": "http://www.legacyapp.com:8080",
                        "condition": "/something222",
                        "status": "undeployed"
                    }
                ])
            ]
        );

        server.respondWith(
            "POST",
            "/openig/api/system/objects/config/weatherapi",
            [
                200,
                {
                    "Content-Type": "application/json;charset=UTF-8"
                },
                JSON.stringify([
                    {
                        "name": "weatherAPI",
                        "url": "http://www.weather.com:8081",
                        "condition": "/something1",
                        "status": "deployed"
                    }
                ])
            ]
        );

        server.respondWith(
            "GET",
            "/openig/api/system/objects/router-handler/routes?_pageSize=10",
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
                            "condition": "${request.uri.path == '/wordpress'}"

                        },
                        {
                            "_id": "legacyapp2", // name
                            "_rev": "1",
                            "uptime": 513246, // ms
                            "condition": "${request.uri.path == '/wordpress'}"
                        }

                    ],
                    "resultCount": 2,
                    "pagedResultsCookie": null,
                    "remainingPagedResults": -1
                })
            ]
        );

    }
));
