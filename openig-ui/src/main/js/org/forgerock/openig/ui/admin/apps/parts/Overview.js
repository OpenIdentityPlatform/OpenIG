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
    "form2js",
    "org/forgerock/openig/ui/admin/apps/AbstractAppView",
    "org/forgerock/commons/ui/common/main/Router"
], (
    $,
    _,
    form2js,
    AbstractAppView,
    Router
) => (
    AbstractAppView.extend({
        element: ".main",
        template: "templates/openig/admin/apps/parts/Overview.html",
        partials: [
            "templates/openig/admin/apps/components/OverviewItem.html"
        ],
        initialize (options) {
            this.data = options.parentData;
            this.data.title = this.data.appData.get("content/name");
            this.data.baseURI = this.data.appData.get("content/url");
            this.data.condition = this.data.appData.get("content/condition");
            this.data.overviewItems = [
                {
                    title: $.t("config.AppConfiguration.Navigation.appsSideMenu.throttling"),
                    route: "appsThrottling",
                    icon: "fa-filter"
                },
                {
                    title: $.t("config.AppConfiguration.Navigation.appsSideMenu.authentication"),
                    route: "appsAuthentication",
                    icon: "fa-user"
                },
                {
                    title: $.t("config.AppConfiguration.Navigation.appsSideMenu.transformation"),
                    route: "appsTransformation",
                    icon: "fa-random"
                }
            ];
        },
        data: {
        },
        render (args) {
            _.forEach(this.data.overviewItems, (item) => {
                item.href = `#${Router.getLink(Router.configuration.routes[item.route], args)}`;
                item.status = this.getStatus(item.route);
            });

            this.parentRender();
        },
        getStatus (route) {
            const filters = this.data.appData.get("content/filters");
            let status = $.t("templates.apps.filters.Off");
            let filter;
            switch (route) {
                case "appsThrottling":
                    filter = _.find(filters, {
                        "type": "ThrottlingFilter",
                        "enabled": true
                    });
                    if (filter) {
                        status = $.t("templates.apps.filters.ThrottlingFilter", {
                            numberOfRequests: filter.numberOfRequests,
                            duration: filter.duration
                        });
                    }
                    break;
                case "appsAuthentication":
                    filter = _.find(filters, {
                        "type": "OAuth2ClientFilter",
                        "enabled": true
                    });
                    if (filter) {
                        status = $.t("templates.apps.filters.OAuth2ClientFilter");
                    }
                    break;
                case "appsTransformation":
                    filter = _.find(filters, {
                        "type": "PasswordReplayFilter",
                        "enabled": true
                    });
                    if (filter) {
                        status = $.t("templates.apps.filters.PasswordReplayFilter");
                    }
                    break;
            }
            return status;
        }


    })
));
