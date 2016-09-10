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
    "underscore",
    "org/forgerock/openig/ui/admin/services/TransformService",
    "org/forgerock/openig/ui/admin/models/AppModel",
    "org/forgerock/openig/ui/common/util/Constants"
], (
    $,
    _,
    transformService,
    AppModel,
    Constants
) => ({
    executeAll () {
        QUnit.module("TransformService TestSuite");

        QUnit.asyncTest("Should fail when transforming undefined model", (assert) => {
            assert.throws(() => {
                transformService.transformApplication(undefined);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing an undefined throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should fail when transforming null model", (assert) => {
            assert.throws(() => {
                transformService.transformApplication(null);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing a null throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should fail when no '_id' attribute is provided", (assert) => {
            const applicationWithoutId = new AppModel({
                _id: "",
                content: {
                }
            });

            assert.throws(() => {
                transformService.transformApplication(applicationWithoutId);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing model without ID throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should fail when no 'name' attribute is provided", (assert) => {
            const applicationWithEmptyName = new AppModel({
                _id: "modelID",
                content: {
                    name: ""
                }
            });

            assert.throws(() => {
                transformService.transformApplication(applicationWithEmptyName);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing model with empty name throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should fail when no 'baseURI' attribute is provided", (assert) => {
            const applicationWithEmptyBaseUrl = new AppModel({
                _id: "modelID",
                content: {
                    url: ""
                }
            });

            assert.throws(() => {
                transformService.transformApplication(applicationWithEmptyBaseUrl);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing model with empty baseURL throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should fail when no 'condition' attribute is provided", (assert) => {
            const applicationWithEmptyCondition = new AppModel({
                _id: "modelID",
                content: {
                    condition: ""
                }
            });

            assert.throws(() => {
                transformService.transformApplication(applicationWithEmptyCondition);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing model with empty condition throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should map basic model properties to top level route attributes", (assert) => {
            const app = new AppModel({
                _id: "modelID",
                content: {
                    id: "modelID",
                    name: "Router",
                    url: "http://www.example.com:8081",
                    condition: "${request.uri.path == '/'}"
                }
            });

            assert.deepEqual(transformService.transformApplication(app),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path == '/'}",
                    "handler": "ClientHandler"
                },
                "Wrong top level properties"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should transform ThrottlingFilter", (assert) => {
            assert.deepEqual(transformService.throttlingFilter({
                numberOfRequests: 60,
                durationValue: 1,
                durationRange: Constants.timeSlot.MINUTE
            }),
                {
                    "name": "Throttling",
                    "type": "ThrottlingFilter",
                    "config": {
                        "rate": {
                            "numberOfRequests": 60,
                            "duration": "1 m"
                        }
                    }
                },
                "Wrong JSON for ThrottlingFilter"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should transform OAuth2ClientFilter", (assert) => {
            assert.deepEqual(transformService.oAuth2ClientFilter({
                clientEndpoint: "/openid",
                clientId: "*****",
                clientSecret: "*****",
                scopes: "openid address email offline_access",
                tokenEndpointUseBasicAuth: false,
                requireHttps: true,
                issuerWellKnownEndpoint: "https://accounts.google.com/.well-known/openid-configuration"
            }),
                {
                    "type": "OAuth2ClientFilter",
                    "name": "OAuth2Client",
                    "config": {
                        "clientEndpoint": "/openid",
                        "loginHandler": {
                            "failureHandler": {
                                "type": "StaticResponseHandler",
                                "config": {
                                    "status": 500,
                                    "reason": "Error",
                                    "entity": "${attributes.openid}"
                                }
                            }
                        },
                        "registrations": [
                            {
                                "name": "oidc-user-info-client",
                                "type": "ClientRegistration",
                                "config": {
                                    "clientId": "*****",
                                    "clientSecret": "*****",
                                    "issuer": {
                                        "name": "Issuer",
                                        "type": "Issuer",
                                        "config": {
                                            "wellKnownEndpoint":
                                            "https://accounts.google.com/.well-known/openid-configuration"
                                        }
                                    },
                                    "scopes": [
                                        "openid",
                                        "address",
                                        "email",
                                        "offline_access"
                                    ],
                                    "tokenEndpointUseBasicAuth": false
                                }
                            }
                        ],
                        "requireHttps": true
                    }
                },
                "Wrong JSON for OAuth2ClientFilter"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should throw exception for unknown filter type", (assert) => {
            assert.throws(() => {
                transformService.transformFilter({ type: "UnknownTypeOfFilter" });
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing 'UnknownTypeOfFilter' throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should pass eventhough all filters are disabled", (assert) => {
            const app = new AppModel({
                _id: "modelID",
                content: {
                    id: "modelID",
                    name: "Router",
                    url: "http://www.example.com:8081",
                    condition: "${request.uri.path === '/'}",
                    filters: [
                        {
                            enabled: false,
                            type: "ThrottlingFilter",
                            numberOfRequests: 60,
                            durationValue: 1,
                            durationRange: Constants.timeSlot.MINUTE
                        }
                    ]
                }
            });

            assert.deepEqual(transformService.transformApplication(app),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": "ClientHandler"
                },
                "Wrong number of filters when all of them are disabled"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should pass only enabled filters", (assert) => {
            const app = new AppModel({
                _id: "modelID",
                content: {
                    id: "modelID",
                    name: "Router",
                    url: "http://www.example.com:8081",
                    condition: "${request.uri.path === '/'}",
                    filters: [
                        {
                            enabled: true,
                            type: "ThrottlingFilter",
                            numberOfRequests: 60,
                            durationValue: 1,
                            durationRange: Constants.timeSlot.MINUTE
                        }
                    ]
                }
            });

            assert.deepEqual(transformService.transformApplication(app),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": {
                        "type": "Chain",
                        "config": {
                            "filters": [{
                                "type": "ThrottlingFilter",
                                "name": "Throttling",
                                "config": {
                                    "rate": {
                                        "numberOfRequests": 60,
                                        "duration": "1 m"
                                    }
                                }
                            }],
                            "handler": "ClientHandler"
                        }
                    }
                },
                "Wrong number of filters when all of them are disabled"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate route level request capture", (assert) => {
            const app = new AppModel({
                _id: "modelID",
                content: {
                    id: "modelID",
                    name: "Router",
                    url: "http://www.example.com:8081",
                    condition: "${request.uri.path === '/'}",
                    capture: {
                        inbound: {
                            request: true,
                            response: false
                        }
                    }
                }
            });

            assert.deepEqual(transformService.transformApplication(app),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": "ClientHandler",
                    "capture": ["request"]
                },
                "Expecting only 'request' for capture"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate route level response capture", (assert) => {
            const app = new AppModel({
                _id: "modelID",
                content: {
                    id: "modelID",
                    name: "Router",
                    url: "http://www.example.com:8081",
                    condition: "${request.uri.path === '/'}",
                    capture: {
                        inbound: {
                            request: false,
                            response: true
                        }
                    }
                }
            });

            assert.deepEqual(transformService.transformApplication(app),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": "ClientHandler",
                    "capture": ["response"]
                },
                "Expecting only 'response' for capture"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate request and response route level capture", (assert) => {
            const app = new AppModel({
                _id: "modelID",
                content: {
                    id: "modelID",
                    name: "Router",
                    url: "http://www.example.com:8081",
                    condition: "${request.uri.path === '/'}",
                    capture: {
                        inbound: {
                            request: true,
                            response: true
                        }
                    }
                }
            });

            assert.deepEqual(transformService.transformApplication(app),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": "ClientHandler",
                    "capture": ["request", "response"]
                },
                "Expecting both 'request' & 'response' for capture"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate outbound request capture", (assert) => {
            const app = new AppModel({
                _id: "modelID",
                content: {
                    id: "modelID",
                    name: "Router",
                    url: "http://www.example.com:8081",
                    condition: "${request.uri.path === '/'}",
                    capture: {
                        outbound: {
                            request: true,
                            response: false
                        }
                    }
                }
            });

            assert.deepEqual(
                transformService.transformApplication(app),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": "ClientHandler",
                    "heap": [
                        {
                            "type": "ClientHandler",
                            "name": "ClientHandler",
                            "capture": ["request"]
                        }
                    ]
                },
                "Expecting only 'request' for capture"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate outbound response capture", (assert) => {
            const app = new AppModel({
                _id: "modelID",
                content: {
                    id: "modelID",
                    name: "Router",
                    url: "http://www.example.com:8081",
                    condition: "${request.uri.path === '/'}",
                    capture: {
                        outbound: {
                            request: false,
                            response: true
                        }
                    }
                }
            });

            assert.deepEqual(
                transformService.transformApplication(app),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": "ClientHandler",
                    "heap": [
                        {
                            "type": "ClientHandler",
                            "name": "ClientHandler",
                            "capture": ["response"]
                        }
                    ]

                },
                "Expecting only 'response' for capture"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate request and response outbound capture", (assert) => {
            const app = new AppModel({
                _id: "modelID",
                content: {
                    id: "modelID",
                    name: "Router",
                    url: "http://www.example.com:8081",
                    condition: "${request.uri.path === '/'}",
                    capture: {
                        outbound: {
                            request: true,
                            response: true
                        }
                    }
                }
            });

            assert.deepEqual(
                transformService.transformApplication(app),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": "ClientHandler",
                    "heap": [
                        {
                            "type": "ClientHandler",
                            "name": "ClientHandler",
                            "capture": ["request", "response"]
                        }
                    ]

                },
                "Expecting both 'request' & 'response' for capture"
            );
            QUnit.start();
        });
    }
}));
