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

define([], function () {
    return {
        moduleDefinition: [
            {
                moduleClass: "org/forgerock/commons/ui/common/main/SessionManager",
                configuration: {
                    loginHelperClass: "org/forgerock/openig/ui/user/login/InternalLoginHelper"
                }
            },
            {
                moduleClass: "org/forgerock/commons/ui/common/SiteConfigurator",
                configuration: {
                    "delegate": "org/forgerock/openig/ui/common/delegates/SiteConfigurationDelegate",
                    "remoteConfig": true
                }
            },

            {
                moduleClass: "org/forgerock/commons/ui/common/main/ProcessConfiguration",
                configuration: {
                    processConfigurationFiles: [
                        "config/process/OpenIGConfig",
                        "config/process/CommonConfig"
                    ]
                }
            },
            {
                moduleClass: "org/forgerock/commons/ui/common/main/Router",
                configuration: {
                    routes: {
                    },
                    loader: [
                        { "routes": "config/routes/CommonRoutesConfig" },
                        { "routes": "config/routes/OpenIGRoutesConfig" },
                        { "routes": "config/routes/UserRoutesConfig" }
                    ]
                }
            },
            {
                moduleClass: "org/forgerock/commons/ui/common/main/ServiceInvoker",
                configuration: {
                    defaultHeaders: {
                    }
                }
            },
            {
                moduleClass: "org/forgerock/commons/ui/common/main/ErrorsHandler",
                configuration: {
                    defaultHandlers: {
                    },
                    loader: [
                        { "defaultHandlers": "config/errorhandlers/CommonErrorHandlers" }
                    ]
                }
            },
            {
                moduleClass: "org/forgerock/commons/ui/common/components/Navigation",
                configuration: {
                    userBar: [
                        {
                            "id": "profile_link",
                            "href": "#profile/details",
                            "i18nKey": "common.user.profile"
                        },
                        {
                            "id": "change_password",
                            "href": "#profile/password",
                            "i18nKey": "common.user.changePassword"
                        },
                        {
                            "id": "logout_link",
                            "href": "#logout/",
                            "i18nKey": "common.form.logout"
                        }
                    ],
                    links: {
                        user: {
                            urls: {
                                "apps": {
                                    "url": "#apps/",
                                    "name": "config.AppConfiguration.Navigation.links.apps",
                                    "icon": "fa fa-rocket",
                                    "inactive": false
                                },
                                "settings": {
                                    "url": "#settings/",
                                    "name": "config.AppConfiguration.Navigation.links.settings",
                                    "icon": "fa fa-cog",
                                    "inactive": false
                                }
                            }
                        },
                        "admin": {
                            "role": "ui-admin",
                            "urls": {
                                "dashboard": {
                                    "url": "#dashboard/",
                                    "name": "config.AppConfiguration.Navigation.links.dashboard",
                                    "icon": "fa fa-dashboard",
                                    "inactive": false
                                },
                                "apps": {
                                    "url": "#dashboard/",
                                    "name": "config.AppConfiguration.Navigation.links.apps",
                                    "icon": "fa fa-dashboard",
                                    "inactive": false
                                },
                                "profile": {
                                    "url": "#profile/",
                                    "name": "common.user.profile",
                                    "icon": "fa fa-user",
                                    "inactive": false
                                }
                            }

                        }
                    }
                }

            },
            {
                moduleClass: "org/forgerock/commons/ui/common/util/UIUtils",
                configuration: {
                    templateUrls: [
                    ]
                }
            },
            {
                moduleClass: "org/forgerock/commons/ui/common/components/Messages",
                configuration: {
                    messages: {
                    },
                    loader: [
                        { "messages": "config/messages/CommonMessages" },
                        { "messages": "config/messages/UserMessages" },
                        { "messages": "config/messages/OpenIGMessages" }
                    ]
                }
            },
            {
                moduleClass: "org/forgerock/commons/ui/common/main/ValidatorsManager",
                configuration: {
                    validators: {},
                    loader: [
                        { "validators": "config/validators/CommonValidators" },
                        { "validators": "config/validators/OpenIGValidators" }
                    ]
                }
            }
        ],
        loggerLevel: "debug"
    };
});
