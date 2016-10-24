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
    "form2js",
    "i18next",
    "org/forgerock/openig/ui/admin/routes/AbstractRouteView"
], (
    $,
    _,
    form2js,
    i18n,
    AbstractRouteView
) => (
    AbstractRouteView.extend({
        element: ".main",
        template: "templates/openig/admin/routes/parts/Overview.html",
        partials: [
            "templates/openig/admin/routes/components/OverviewItem.html"
        ],
        initialize (options) {
            this.data = options.parentData;
            this.data.routeId = this.data.routeData.get("id");
            this.data.title = this.data.routeData.get("name");
            this.data.baseURI = this.data.routeData.get("baseURI");
            this.data.condition = this.data.routeData.get("condition");
            this.data.overviewItems = [
                {
                    title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.capture"),
                    route: "routeCapture",
                    icon: "fa-search"
                },
                {
                    title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.throttling"),
                    route: "routeThrottling",
                    icon: "fa-filter"
                },
                {
                    title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.authentication"),
                    route: "routeAuthentication",
                    icon: "fa-user"
                },
                {
                    title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.authorization"),
                    route: "routeAuthorization",
                    icon: "fa-key"
                },
                {
                    title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.statistics"),
                    route: "routeStatistics",
                    icon: "fa-line-chart"
                }
            ];
        },
        data: {
        },
        render () {
            _.forEach(this.data.overviewItems, (item) => {
                item.routeId = this.data.routeId;
                item.status = this.getStatus(item.route);
            });

            this.parentRender();
        },
        getStatus (route) {
            const filters = this.data.routeData.get("filters");
            let status = i18n.t("templates.routes.filters.Off");
            let filter;
            switch (route) {
                case "routeCapture":
                    const capture = this.data.routeData.get("capture");
                    let inbound;
                    if (_.get(capture, "inbound.request") && _.get(capture, "inbound.response")) {
                        inbound = i18n.t("templates.routes.capture.inboundMessages");
                    } else if (_.get(capture, "inbound.request")) {
                        inbound = i18n.t("templates.routes.capture.inboundRequests");
                    } else if (_.get(capture, "inbound.response")) {
                        inbound = i18n.t("templates.routes.capture.inboundResponses");
                    }

                    let outbound;
                    if (_.get(capture, "outbound.request") && _.get(capture, "outbound.response")) {
                        outbound = i18n.t("templates.routes.capture.outboundMessages");
                    } else if (_.get(capture, "outbound.request")) {
                        outbound = i18n.t("templates.routes.capture.outboundRequests");
                    } else if (_.get(capture, "outbound.response")) {
                        outbound = i18n.t("templates.routes.capture.outboundResponses");
                    }

                    if (inbound && outbound) {
                        status = `${inbound},  ${outbound}`;
                    } else if (inbound) {
                        status = inbound;
                    } else if (outbound) {
                        status = outbound;
                    }
                    break;
                case "routeThrottling":
                    filter = _.find(filters, {
                        "type": "ThrottlingFilter",
                        "enabled": true
                    });
                    if (filter) {
                        status = i18n.t("templates.routes.filters.ThrottlingFilter", {
                            numberOfRequests: filter.numberOfRequests,
                            duration: filter.durationValue,
                            durationRange: i18n.t(`common.timeSlot.${filter.durationRange}`)
                        });
                    }
                    break;
                case "routeAuthentication":
                    filter = _.find(filters, {
                        "type": "OAuth2ClientFilter",
                        "enabled": true
                    });
                    if (filter) {
                        status = i18n.t("templates.routes.filters.OAuth2ClientFilter");
                    }
                    break;
                case "routeAuthorization":
                    filter = _.find(filters, {
                        "type": "PolicyEnforcementFilter",
                        "enabled": true
                    });
                    if (filter) {
                        status = i18n.t("templates.routes.filters.PolicyEnforcementFilter");
                    }
                    break;
                case "routeStatistics":
                    const statistics = this.data.routeData.get("statistics");
                    if (_.get(statistics, "enabled")) {
                        status = i18n.t("templates.routes.parts.statistics.fields.status");
                    }
            }
            return status;
        }


    })
));
