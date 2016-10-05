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
    "i18next",
    "org/forgerock/openig/ui/admin/apps/AbstractAppView"
], (
    $,
    _,
    form2js,
    i18n,
    AbstractAppView
) => (
    AbstractAppView.extend({
        element: ".main",
        template: "templates/openig/admin/apps/parts/Overview.html",
        partials: [
            "templates/openig/admin/apps/components/OverviewItem.html"
        ],
        initialize (options) {
            this.data = options.parentData;
            this.data.appId = this.data.appData.get("_id");
            this.data.title = this.data.appData.get("content/name");
            this.data.baseURI = this.data.appData.get("content/baseURI");
            this.data.condition = this.data.appData.get("content/condition");
            this.data.overviewItems = [
                {
                    title: i18n.t("config.AppConfiguration.Navigation.appsSideMenu.capture"),
                    route: "appsCapture",
                    icon: "fa-search"
                },
                {
                    title: $.t("config.AppConfiguration.Navigation.appsSideMenu.throttling"),
                    route: "appsThrottling",
                    icon: "fa-filter"
                },
                {
                    title: i18n.t("config.AppConfiguration.Navigation.appsSideMenu.authentication"),
                    route: "appsAuthentication",
                    icon: "fa-user"
                },
                {
                    title: i18n.t("config.AppConfiguration.Navigation.appsSideMenu.authorization"),
                    route: "appsAuthorization",
                    icon: "fa-key"
                },
                {
                    title: i18n.t("config.AppConfiguration.Navigation.appsSideMenu.statistics"),
                    route: "appsStatistics",
                    icon: "fa-line-chart"
                }
            ];
        },
        data: {
        },
        render () {
            _.forEach(this.data.overviewItems, (item) => {
                item.appId = this.data.appId;
                item.status = this.getStatus(item.route);
            });

            this.parentRender();
        },
        getStatus (route) {
            const filters = this.data.appData.get("content/filters");
            let status = i18n.t("templates.apps.filters.Off");
            let filter;
            switch (route) {
                case "appsCapture":
                    const capture = this.data.appData.get("content/capture");
                    let inbound;
                    if (_.get(capture, "inbound.request") && _.get(capture, "inbound.response")) {
                        inbound = i18n.t("templates.apps.capture.inboundMessages");
                    } else if (_.get(capture, "inbound.request")) {
                        inbound = i18n.t("templates.apps.capture.inboundRequests");
                    } else if (_.get(capture, "inbound.response")) {
                        inbound = i18n.t("templates.apps.capture.inboundResponses");
                    }

                    let outbound;
                    if (_.get(capture, "outbound.request") && _.get(capture, "outbound.response")) {
                        outbound = i18n.t("templates.apps.capture.outboundMessages");
                    } else if (_.get(capture, "outbound.request")) {
                        outbound = i18n.t("templates.apps.capture.outboundRequests");
                    } else if (_.get(capture, "outbound.response")) {
                        outbound = i18n.t("templates.apps.capture.outboundResponses");
                    }

                    if (inbound && outbound) {
                        status = `${inbound},  ${outbound}`;
                    } else if (inbound) {
                        status = inbound;
                    } else if (outbound) {
                        status = outbound;
                    }
                    break;
                case "appsThrottling":
                    filter = _.find(filters, {
                        "type": "ThrottlingFilter",
                        "enabled": true
                    });
                    if (filter) {
                        status = i18n.t("templates.apps.filters.ThrottlingFilter", {
                            numberOfRequests: filter.numberOfRequests,
                            duration: filter.durationValue,
                            durationRange: i18n.t(`common.timeSlot.${filter.durationRange}`)
                        });
                    }
                    break;
                case "appsAuthentication":
                    filter = _.find(filters, {
                        "type": "OAuth2ClientFilter",
                        "enabled": true
                    });
                    if (filter) {
                        status = i18n.t("templates.apps.filters.OAuth2ClientFilter");
                    }
                    break;
                case "appsAuthorization":
                    filter = _.find(filters, {
                        "type": "PolicyEnforcementFilter",
                        "enabled": true
                    });
                    if (filter) {
                        status = i18n.t("templates.apps.filters.PolicyEnforcementFilter");
                    }
                    break;
                case "appsStatistics":
                    const statistics = this.data.appData.get("content/statistics");
                    if (_.get(statistics, "enabled")) {
                        status = i18n.t("templates.apps.parts.statistics.fields.status");
                    }
            }
            return status;
        }


    })
));
