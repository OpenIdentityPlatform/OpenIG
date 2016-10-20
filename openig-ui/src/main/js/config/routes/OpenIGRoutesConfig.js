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
    "listRoutesView": {
        view: "org/forgerock/openig/ui/admin/routes/RoutesListView",
        url: "routes/",
        defaults: ["/", ""]
    },
    "welcomePage": {
        view: "org/forgerock/openig/ui/admin/routes/WelcomePage",
        url: "welcome/"
    },
    "addRouteView": {
        view: "org/forgerock/openig/ui/admin/routes/AddRouteView",
        url: "routes/add/"
    },
    "duplicateRouteView": {
        view: "org/forgerock/openig/ui/admin/routes/AddRouteView",
        url: /^routes\/duplicate\/(.+?)$/,
        pattern: "routes/duplicate/?"
    },
    "editRouteView": {
        view: "org/forgerock/openig/ui/admin/routes/RoutesTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/routes/parts/Overview",
        defaults: ["", ""],
        url: /^routes\/edit\/(.+?)\/(.*)$/,
        pattern: "routes/edit/?/?"
    },
    "settings": {
        view: "org/forgerock/openig/ui/admin/settings/SettingsView",
        url: "settings/"
    },
    "routeOverview": {
        view: "org/forgerock/openig/ui/admin/routes/RoutesTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/routes/parts/Overview",
        url: /^routes\/edit\/(.+?)\/overview$/,
        pattern: "routes/edit/?/overview",
        navGroup: "admin",
        forceUpdate: true
    },
    "routeCapture": {
        view: "org/forgerock/openig/ui/admin/routes/RoutesTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/routes/parts/Capture",
        url: /^routes\/edit\/(.+?)\/capture/,
        pattern: "routes/edit/?/capture",
        navGroup: "admin",
        forceUpdate: true
    },
    "routeThrottling": {
        view: "org/forgerock/openig/ui/admin/routes/RoutesTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/routes/parts/Throttling",
        url: /^routes\/edit\/(.+?)\/throttling$/,
        pattern: "routes/edit/?/throttling",
        navGroup: "admin",
        forceUpdate: true
    },
    "routeAuthentication": {
        view: "org/forgerock/openig/ui/admin/routes/RoutesTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/routes/parts/Authentication",
        url: /^routes\/edit\/(.+?)\/authentication$/,
        pattern: "routes/edit/?/authentication",
        navGroup: "admin",
        forceUpdate: true
    },
    "routeAuthorization": {
        view: "org/forgerock/openig/ui/admin/routes/RoutesTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/routes/parts/Authorization",
        url: /^routes\/edit\/(.+?)\/authorization$/,
        pattern: "routes/edit/?/authorization",
        navGroup: "admin",
        forceUpdate: true
    },
    "routeStatistics": {
        view: "org/forgerock/openig/ui/admin/routes/RoutesTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/routes/parts/Statistics",
        url: /^routes\/edit\/(.+?)\/statistics$/,
        pattern: "routes/edit/?/statistics",
        navGroup: "admin",
        forceUpdate: true
    },
    "routeSettings": {
        view: "org/forgerock/openig/ui/admin/routes/RoutesTreeNavigationView",
        page: "org/forgerock/openig/ui/admin/routes/parts/Settings",
        url: /^routes\/edit\/(.+?)\/settings$/,
        pattern: "routes/edit/?/settings",
        navGroup: "admin",
        forceUpdate: true
    }
}));
