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
    "org/forgerock/commons/ui/common/util/Constants"
], (
    Constants
) => ({
    "404": { //this route must be the first route
        view: "org/forgerock/commons/ui/common/NotFoundView",
        url: /^([\w\W]*)$/,
        pattern: "?"
    },
    "default": {
        event: Constants.EVENT_HANDLE_DEFAULT_ROUTE,
        url: /^$/,
        pattern: ""
    },
    "enableCookies": {
        view: "org/forgerock/commons/ui/common/EnableCookiesView",
        url: "enableCookies/"
    },
    "appsPage": {
        view: "org/forgerock/openig/ui/admin/apps/AppsListView",
        url: "apps/",
        defaults: ["/", ""]
    },
    "addAppView": {
        view: "org/forgerock/openig/ui/admin/apps/AddAppView",
        url: "apps/add/"
    },
    "duplicateAppView": {
        view: "org/forgerock/openig/ui/admin/apps/AddAppView",
        url: /^apps\/duplicate\/(.+?)$/,
        pattern: "apps/duplicate/?"
    },
    "editAppView": {
        view: "org/forgerock/openig/ui/admin/apps/AppsTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/apps/parts/Overview",
        defaults: ["", ""],
        url: /^apps\/edit\/(.+?)\/(.*)$/,
        pattern: "apps/edit/?/?"
    },
    "settings": {
        view: "org/forgerock/openig/ui/admin/settings/SettingsView",
        url: "settings/"
    },
    "appsOverview": {
        view: "org/forgerock/openig/ui/admin/apps/AppsTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/apps/parts/Overview",
        url: /^apps\/edit\/(.+?)\/overview$/,
        pattern: "apps/edit/?/overview",
        navGroup: "admin",
        forceUpdate: true
    },
    "appsCapture": {
        view: "org/forgerock/openig/ui/admin/apps/AppsTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/apps/parts/Capture",
        url: /^apps\/edit\/(.+?)\/capture/,
        pattern: "apps/edit/?/capture",
        navGroup: "admin",
        forceUpdate: true
    },
    "appsThrottling": {
        view: "org/forgerock/openig/ui/admin/apps/AppsTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/apps/parts/Throttling",
        url: /^apps\/edit\/(.+?)\/throttling$/,
        pattern: "apps/edit/?/throttling",
        navGroup: "admin",
        forceUpdate: true
    },
    "appsAuthentication": {
        view: "org/forgerock/openig/ui/admin/apps/AppsTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/apps/parts/Authentication",
        url: /^apps\/edit\/(.+?)\/authentication$/,
        pattern: "apps/edit/?/authentication",
        navGroup: "admin",
        forceUpdate: true
    },
    "appsAuthorization": {
        view: "org/forgerock/openig/ui/admin/apps/AppsTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/apps/parts/Authorization",
        url: /^apps\/edit\/(.+?)\/authorization$/,
        pattern: "apps/edit/?/authorization",
        navGroup: "admin",
        forceUpdate: true
    },
    "appsMonitoring": {
        view: "org/forgerock/openig/ui/admin/apps/AppsTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/apps/parts/Monitoring",
        url: /^apps\/edit\/(.+?)\/monitoring$/,
        pattern: "apps/edit/?/monitoring",
        navGroup: "admin",
        forceUpdate: true
    },
    "appsSettings": {
        view: "org/forgerock/openig/ui/admin/apps/AppsTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/apps/parts/Settings",
        url: /^apps\/edit\/(.+?)\/settings$/,
        pattern: "apps/edit/?/settings",
        navGroup: "admin",
        forceUpdate: true
    }
}));
