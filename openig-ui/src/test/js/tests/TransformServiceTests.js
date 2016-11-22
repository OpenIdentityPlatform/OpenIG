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
    "lodash",
    "org/forgerock/openig/ui/admin/services/TransformService",
    "org/forgerock/openig/ui/admin/models/RouteModel",
    "org/forgerock/openig/ui/common/util/Constants"
], (
    $,
    _,
    transformService,
    RouteModel,
    Constants
) => ({
    executeAll () {
        QUnit.module("TransformService TestSuite");

        QUnit.asyncTest("Should fail when transforming undefined model", (assert) => {
            assert.throws(() => {
                transformService.transformRoute(undefined);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing an undefined throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should fail when transforming null model", (assert) => {
            assert.throws(() => {
                transformService.transformRoute(null);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing a null throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should fail when no 'name' attribute is provided", (assert) => {
            const applicationWithEmptyName = new RouteModel({
                id: "modelID",
                name: ""
            });

            assert.throws(() => {
                transformService.transformRoute(applicationWithEmptyName);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing model with empty name throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should fail when no 'baseURI' attribute is provided", (assert) => {
            const applicationWithEmptyBaseUrl = new RouteModel({
                id: "modelID",
                url: ""
            });

            assert.throws(() => {
                transformService.transformRoute(applicationWithEmptyBaseUrl);
            },
                transformService.TransformServiceException("invalidModel"),
                "Passing model with empty baseURL throws an error"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should transform 'path condition' attribute", (assert) => {
            const applicationWithPathCondition = new RouteModel({
                id: "example",
                name: "example",
                baseURI: "http://www.example.com:8081",
                condition: {
                    type: "path",
                    path: "/testpath",
                    expression: "${request.uri.path === '/'}"
                }
            });

            assert.deepEqual(transformService.transformRoute(applicationWithPathCondition),
                {
                    "name": "example",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "condition": "${matches(request.uri.path, '^/testpath')}",
                    "monitor": false
                },
                "Wrong top level properties"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should transform 'expression condition' attribute", (assert) => {
            const applicationWithPathCondition = new RouteModel({
                id: "example",
                name: "example",
                baseURI: "http://www.example.com:8081",
                condition: {
                    type: "expression",
                    path: "/testpath",
                    expression: "${request.uri.path === '/'}"
                }
            });

            assert.deepEqual(transformService.transformRoute(applicationWithPathCondition),
                {
                    "name": "example",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "condition": "${request.uri.path === '/'}",
                    "monitor": false
                },
                "Wrong top level properties"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should map basic model properties to top level route attributes", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081"
            });

            assert.deepEqual(transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": false
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
                        "failureHandler": {
                            "type": "StaticResponseHandler",
                            "config": {
                                "status": 500,
                                "reason": "Error",
                                "entity": "${attributes.openid}"
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

        QUnit.asyncTest("Should pass even if all filters are disabled", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                filters: [
                    {
                        enabled: false,
                        type: "ThrottlingFilter",
                        numberOfRequests: 60,
                        durationValue: 1,
                        durationRange: Constants.timeSlot.MINUTE
                    }
                ]
            });

            assert.deepEqual(transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": false
                },
                "Wrong number of filters when all of them are disabled"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should pass only enabled filters", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                filters: [
                    {
                        enabled: true,
                        type: "ThrottlingFilter",
                        numberOfRequests: 60,
                        durationValue: 1,
                        durationRange: Constants.timeSlot.MINUTE
                    }
                ]
            });

            assert.deepEqual(transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "monitor": false,
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
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                capture: {
                    inbound: {
                        request: true,
                        response: false
                    }
                }
            });

            assert.deepEqual(transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": false,
                    "capture": ["request"]
                },
                "Expecting only 'request' for capture"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate route level response capture", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                capture: {
                    inbound: {
                        request: false,
                        response: true
                    }
                }
            });

            assert.deepEqual(transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": false,
                    "capture": ["response"]
                },
                "Expecting only 'response' for capture"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate request and response route level capture", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                capture: {
                    inbound: {
                        request: true,
                        response: true
                    }
                }
            });

            assert.deepEqual(transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": false,
                    "capture": ["request", "response"]
                },
                "Expecting both 'request' & 'response' for capture"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate outbound request capture", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                capture: {
                    outbound: {
                        request: true,
                        response: false
                    }
                }
            });

            assert.deepEqual(
                transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": false,
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
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                capture: {
                    outbound: {
                        request: false,
                        response: true
                    }
                }
            });

            assert.deepEqual(
                transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": false,
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
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                capture: {
                    outbound: {
                        request: true,
                        response: true
                    }
                }
            });

            assert.deepEqual(
                transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": false,
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

        QUnit.asyncTest("Should activate entity capture", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                condition: "${request.uri.path === '/'}",
                capture: {
                    entity: true
                }
            });

            assert.deepEqual(
                transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": "ClientHandler",
                    "monitor": false,
                    "heap": [
                        {
                            "type": "CaptureDecorator",
                            "name": "capture",
                            "config": {
                                "captureEntity": true
                            }
                        }
                    ]

                },
                "Expecting entity capture enabled"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should activate entity capture", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                condition: "${request.uri.path === '/'}",
                capture: {
                    entity: false
                }
            });

            assert.deepEqual(
                transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "condition": "${request.uri.path === '/'}",
                    "handler": "ClientHandler",
                    "monitor": false
                },
                "Expecting entity capture enabled"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should enable statistics", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                statistics: {
                    enabled: true
                }
            });

            assert.deepEqual(transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": true
                },
                "Expecting monitor enabled"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should enable statistics and add percentiles", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                statistics: {
                    enabled: true,
                    percentiles: "0.99 0.999 0.9999"
                }
            });

            assert.deepEqual(transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": {
                        "enabled": true,
                        "percentiles": [0.99, 0.999, 0.9999]
                    }
                },
                "Expecting monitor enabled with percentiles"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should disable statistics and remove percentiles", (assert) => {
            const route = new RouteModel({
                id: "modelID",
                name: "Router",
                baseURI: "http://www.example.com:8081",
                statistics: {
                    enabled: false,
                    percentiles: "0.99 0.999 0.9999"
                }
            });

            assert.deepEqual(transformService.transformRoute(route),
                {
                    "name": "modelID",
                    "baseURI": "http://www.example.com:8081",
                    "handler": "ClientHandler",
                    "monitor": false
                },
                "Expecting monitor disabled"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should produce contextual info for multiple headers", (assert) => {
            const filter = {
                type: "PolicyEnforcementFilter",
                openamUrl: "http://openam.example.com/openam",
                pepUsername: "bob",
                pepPassword: "the-sponge",
                pepRealm: "cartoon",
                realm: "users",
                ssoTokenSubject: "John",
                application: "PS",
                headers: "User-Agent Referer"
            };

            assert.deepEqual(transformService.policyEnforcementFilter(filter),
                {
                    type: "PolicyEnforcementFilter",
                    name: "PEPFilter",
                    config: {
                        openamUrl: "http://openam.example.com/openam",
                        pepUsername: "bob",
                        pepPassword: "the-sponge",
                        pepRealm: "cartoon",
                        realm: "users",
                        ssoTokenSubject: "John",
                        application: "PS",
                        environment: {
                            "H-User-Agent": "${request.headers['User-Agent']}",
                            "H-Referer": "${request.headers['Referer']}"
                        }
                    }
                },
                "Expecting policy enforcement filter with headers in environment"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should not produce contextual info for empty header", (assert) => {
            const filter = {
                type: "PolicyEnforcementFilter",
                openamUrl: "http://openam.example.com/openam",
                pepUsername: "bob",
                pepPassword: "the-sponge",
                pepRealm: "cartoon",
                realm: "users",
                ssoTokenSubject: "John",
                application: "PS",
                headers: ""
            };

            assert.deepEqual(transformService.policyEnforcementFilter(filter),
                {
                    type: "PolicyEnforcementFilter",
                    name: "PEPFilter",
                    config: {
                        openamUrl: "http://openam.example.com/openam",
                        pepUsername: "bob",
                        pepPassword: "the-sponge",
                        pepRealm: "cartoon",
                        realm: "users",
                        ssoTokenSubject: "John",
                        application: "PS"
                    }
                },
                "Expecting policy enforcement filter with empty environment"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should produce contextual info with client IP address", (assert) => {
            const filter = {
                type: "PolicyEnforcementFilter",
                openamUrl: "http://openam.example.com/openam",
                pepUsername: "bob",
                pepPassword: "the-sponge",
                pepRealm: "cartoon",
                realm: "users",
                ssoTokenSubject: "John",
                application: "PS",
                address: true
            };

            assert.deepEqual(transformService.policyEnforcementFilter(filter),
                {
                    type: "PolicyEnforcementFilter",
                    name: "PEPFilter",
                    config: {
                        openamUrl: "http://openam.example.com/openam",
                        pepUsername: "bob",
                        pepPassword: "the-sponge",
                        pepRealm: "cartoon",
                        realm: "users",
                        ssoTokenSubject: "John",
                        application: "PS",
                        environment: {
                            "IP": ["${contexts.client.remoteAddress}"]
                        }
                    }
                },
                "Expecting policy enforcement filter with IP in environment"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should transform PolicyEnforcementFilter with ssoTokenSubject", (assert) => {
            // Optional values omitted intentionally
            assert.deepEqual(transformService.policyEnforcementFilter(
                {
                    openamUrl: "http://openam.example.com/openam",
                    pepUsername: "amadmin",
                    pepPassword: "secret",
                    ssoTokenSubject: "${attributes.ssoToken.value}"
                }),
                {
                    type: "PolicyEnforcementFilter",
                    name: "PEPFilter",
                    config: {
                        openamUrl: "http://openam.example.com/openam",
                        pepUsername: "amadmin",
                        pepPassword: "secret",
                        ssoTokenSubject: "${attributes.ssoToken.value}"
                    }
                },
                "Wrong JSON for PolicyEnforcementFilter"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should transform PolicyEnforcementFilter with jwtSubject", (assert) => {
            // Optional values set to empty strings intentionally
            assert.deepEqual(transformService.policyEnforcementFilter(
                {
                    openamUrl: "http://openam.example.com/openam",
                    pepUsername: "amadmin",
                    pepPassword: "secret",
                    pepRealm: "",
                    realm: "",
                    application: "",
                    jwtSubject: "${attributes.openid.id_token}"
                }),
                {
                    type: "PolicyEnforcementFilter",
                    name: "PEPFilter",
                    config: {
                        openamUrl: "http://openam.example.com/openam",
                        pepUsername: "amadmin",
                        pepPassword: "secret",
                        jwtSubject: "${attributes.openid.id_token}"
                    }
                },
                "Wrong JSON for PolicyEnforcementFilter"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should transform PolicyEnforcementFilter with all properties", (assert) => {
            // Optional values are correctly valued
            assert.deepEqual(transformService.policyEnforcementFilter(
                {
                    openamUrl: "http://openam.example.com/openam",
                    pepUsername: "amadmin",
                    pepPassword: "secret",
                    pepRealm: "/",
                    realm: "/employees",
                    application: "My Policy Set",
                    ssoTokenSubject: "${attributes.ssoToken.value}",
                    jwtSubject: "${attributes.openid.id_token}"
                }),
                {
                    type: "PolicyEnforcementFilter",
                    name: "PEPFilter",
                    config: {
                        openamUrl: "http://openam.example.com/openam",
                        pepUsername: "amadmin",
                        pepPassword: "secret",
                        pepRealm: "/",
                        realm: "/employees",
                        application: "My Policy Set",
                        ssoTokenSubject: "${attributes.ssoToken.value}",
                        jwtSubject: "${attributes.openid.id_token}"
                    }
                },
                "Wrong JSON for PolicyEnforcementFilter"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should fail to transform PolicyEnforcementFilter with no subject", (assert) => {
            const inBlock = () => {
                transformService.policyEnforcementFilter(
                    {
                        openamUrl: "http://openam.example.com/openam",
                        pepUsername: "amadmin",
                        pepPassword: "secret"
                    });
            };
            assert.throws(inBlock, transformService.TransformServiceException("invalidModel"), "Must provide subject");
            QUnit.start();
        });

        QUnit.asyncTest("Transform SingleSignOnFilter", (assert) => {
            assert.deepEqual(transformService.singleSignOnFilter(
                {
                    type: "SingleSignOnFilter",
                    openamUrl: "http://openam.example.com/openam",
                    realm: "/",
                    cookieName: "iPlanetDirectoryPro"
                }),
                {
                    type: "SingleSignOnFilter",
                    name: "SingleSignOn",
                    config: {
                        openamUrl: "http://openam.example.com/openam",
                        realm: "/",
                        cookieName: "iPlanetDirectoryPro"
                    }
                },
                "SingleSignOnFilter with all properties"
            );

            assert.deepEqual(transformService.singleSignOnFilter(
                {
                    type: "SingleSignOnFilter",
                    openamUrl: "http://openam.example.com/openam",
                    realm: "",
                    cookieName: ""
                }),
                {
                    type: "SingleSignOnFilter",
                    name: "SingleSignOn",
                    config: {
                        openamUrl: "http://openam.example.com/openam"
                    }
                },
                "SingleSignOnFilter with only openamUrl"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should return generated expression", (assert) => {
            const path = "/myApplication";

            assert.deepEqual(transformService.generateCondition(path),
                "${matches(request.uri.path, '^/myApplication')}",
                "Expecting condition expression"
            );
            QUnit.start();
        });

        QUnit.asyncTest("Should return undefined if no path defined", (assert) => {
            const path = "";

            assert.equal(transformService.generateCondition(path),
                undefined,
                "Expecting undefined"
            );
            QUnit.start();
        });
    }
}));
