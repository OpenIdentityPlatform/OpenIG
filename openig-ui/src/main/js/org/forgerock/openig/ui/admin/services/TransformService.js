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

define(["lodash"], (_) => ({

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
                    numberOfRequests: filter.numberOfRequests,
                    duration: filter.duration
                }
            }
        };
    },

    passwordReplayFilter (filter) {
        return {
            type: "PasswordReplayFilter",
            name: "PasswordReplay",
            config: {
                request: {
                    uri: filter.request.uri,
                    method: filter.request.method
                },
                loginPage: filter.loginPage
            }
        };
    },

    oAuth2ClientFilter (filter) {
        return {
            type: "OAuth2ClientFilter",
            name: "OAuth2Client",
            config: {
                clientEndpoint: filter.clientEndpoint,
                failureHandler: "Dump",
                defaultLoginGoto: filter.loginUri,
                defaultLogoutGoto: filter.logoutUri,
                metadata: filter.metadata
            }
        };
    },

    transformFilter (filter) {
        // Tranform Filter from the app model into JSON
        switch (filter.type) {
            case "ThrottlingFilter":
                return this.throttlingFilter(filter);
            // TODO: Not needed filters in this commit;
            // Will be uncommented (amended) in next sprints
            case "OAuth2ClientFilter":
                // return this.oAuth2ClientFilter(filter);
                break;
            case "PasswordReplayFilter":
                // return this.passwordReplayFilter(filter);
                break;
            default:
                throw new this.TransformServiceException("unknownFilterType", filter.type);
        }
    },

    transformApplication (app) {
        if (app === undefined || app === null || !app.isValid()) {
            throw new this.TransformServiceException("invalidModel");
        }

        // Base route attributes
        const route = {
            name: app.get("_id"),
            baseURI: app.get("content/url"),
            condition: app.get("content/condition")
        };

        // Convert all filters
        const chain = this.chainBuilder();
        const filters = app.get("content/filters");
        _.forEach(filters, (filter) => {
            if (filter.enabled === true) {
                const generatedFilter = this.transformFilter(filter);
                if (generatedFilter instanceof Object) {
                    chain.filters.push(generatedFilter);
                }
            }
        });

        // Create the main "handler" attribute with the configured chain
        const handlerType = "ClientHandler";
        route.handler = _.size(chain.filters) === 0 ? handlerType : chain.build(handlerType);
        return JSON.stringify(route);
    }
}));
