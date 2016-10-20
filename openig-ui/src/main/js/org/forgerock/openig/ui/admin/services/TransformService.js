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
    "org/forgerock/openig/ui/admin/util/ValueHelper"
], (
    _,
    ValueHelper
) => ({

    TransformServiceException (type, message) {
        this.errorType = type;
        this.message = message;
        this.name = "TransformServiceException";
    },

    chainBuilder () {
        return {
            filters: [],
            build (terminal) {
                // construct the JSON for a Chain
                return {
                    type: "Chain",
                    config: {
                        filters: this.filters,
                        handler: terminal
                    }
                };
            }
        };
    },

    throttlingFilter (filter) {
        return {
            type: "ThrottlingFilter",
            name: "Throttling",
            config: {
                rate: {
                    numberOfRequests: ValueHelper.toNumber(filter.numberOfRequests),
                    duration: `${filter.durationValue} ${filter.durationRange}`
                }
            }
        };
    },

    oAuth2ClientFilter (filter) {
        return {
            type: "OAuth2ClientFilter",
            name: "OAuth2Client",
            config: {
                clientEndpoint: filter.clientEndpoint,
                failureHandler: this.createFailureHandler(),
                registrations: [{
                    name: "oidc-user-info-client",
                    type: "ClientRegistration",
                    config: {
                        clientId: filter.clientId,
                        clientSecret: filter.clientSecret,
                        issuer: {
                            name: "Issuer",
                            type: "Issuer",
                            config: {
                                wellKnownEndpoint: filter.issuerWellKnownEndpoint
                            }
                        },
                        scopes: filter.scopes.split(" "),
                        tokenEndpointUseBasicAuth: filter.tokenEndpointUseBasicAuth
                    }
                }],
                requireHttps: filter.requireHttps
            }
        };
    },

    policyEnforcementFilter (filter) {
        return {
            type: "PolicyEnforcementFilter",
            name: "PEPFilter",
            config: {
                openamUrl: filter.openamUrl,
                pepUsername: filter.pepUsername,
                pepPassword: filter.pepPassword,
                pepRealm: filter.pepRealm,
                realm: filter.realm,
                ssoTokenSubject: filter.ssoTokenSubject,
                application: filter.application
            }
        };
    },

    // In this version is the createFailureHandler method hardcoded
    createFailureHandler () {
        return {
            type: "StaticResponseHandler",
            config: {
                // "Trivial failure handler for debugging only"
                status: 500,
                reason: "Error",
                entity: "${attributes.openid}"
            }
        };
    },

    transformFilter (filter) {
        // Transform Filter from the route model into JSON
        switch (filter.type) {
            case "ThrottlingFilter":
                return this.throttlingFilter(filter);
            case "OAuth2ClientFilter":
                return this.oAuth2ClientFilter(filter);
            case "PolicyEnforcementFilter":
                return this.policyEnforcementFilter(filter);
            default:
                throw new this.TransformServiceException("unknownFilterType", filter.type);
        }
    },

    heapOf (route) {
        if (!route.heap) {
            route.heap = [];
        }
        return route.heap;

    },

    newClientHandler () {
        return {
            type: "ClientHandler",
            name: "ClientHandler"
        };
    },

    decorate (declaration, decorator, decoration) {
        declaration[decorator] = decoration;
        return declaration;
    },

    transformStatistics (statistics) {
        if (!statistics) {
            return false;
        }

        return (statistics.percentiles && statistics.enabled)
            ? {
                enabled: true,
                percentiles: _.map(statistics.percentiles.split(" "), (o) => ValueHelper.toNumber(o))
            }
            : statistics.enabled;
    },

    transformRoute (route) {
        if (route === undefined || route === null || !route.isValid()) {
            throw new this.TransformServiceException("invalidModel");
        }

        // Base route attributes
        const routeConfig = {
            name: route.get("id"),
            baseURI: route.get("baseURI"),
            condition: route.get("condition"),
            monitor: this.transformStatistics(route.get("statistics"))
        };

        // Convert all filters
        const chain = this.chainBuilder();
        const filters = route.get("filters");
        _.forEach(filters, (filter) => {
            if (filter.enabled === true) {
                const generatedFilter = this.transformFilter(filter);
                if (generatedFilter instanceof Object) {
                    chain.filters.push(generatedFilter);
                }
            }
        });

        // Inbound messages capture
        const capture = route.get("capture");
        if (capture) {
            if (capture.inbound) {
                const captured = [];
                _.forEach(capture.inbound, (value, key) => {
                    if (value) {
                        captured.push(key);
                    }
                });
                if (!_.isEmpty(captured)) {
                    routeConfig.capture = captured;
                }
            }
            if (capture.outbound) {
                const captured = [];
                _.forEach(capture.outbound, (value, key) => {
                    if (value) {
                        captured.push(key);
                    }
                });
                if (!_.isEmpty(captured)) {
                    this.heapOf(routeConfig).push(this.decorate(this.newClientHandler(), "capture", captured));
                }
            }
        }

        // Create the main "handler" attribute with the configured chain
        const terminal = "ClientHandler";
        routeConfig.handler = _.size(chain.filters) === 0 ? terminal : chain.build(terminal);
        return routeConfig;
    }
}));
