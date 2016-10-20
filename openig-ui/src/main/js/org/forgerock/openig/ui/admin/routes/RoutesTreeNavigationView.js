/*
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
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/common/components/TreeNavigation",
    "org/forgerock/openig/ui/admin/views/common/navigation/createTreeNavigation",
    "org/forgerock/openig/ui/admin/models/RoutesCollection",
    "org/forgerock/openig/ui/admin/models/ServerRoutesCollection",
    "org/forgerock/openig/ui/admin/util/RoutesUtils"
], ($, _, Constants, EventManager, Router,
    TreeNavigation,
    createTreeNavigation,
    RoutesCollection,
    ServerRoutesCollection,
    RoutesUtils
) => {
    const navData = [{
        title: "config.AppConfiguration.Navigation.routeSideMenu.overview",
        icon: "fa-home",
        route: "routeOverview"
    }, {
        title: "config.AppConfiguration.Navigation.routeSideMenu.capture",
        icon: "fa-search",
        route:"routeCapture"
    }, {
        title: "config.AppConfiguration.Navigation.routeSideMenu.throttling",
        icon: "fa-filter",
        route:"routeThrottling"
    }, {
        title: "config.AppConfiguration.Navigation.routeSideMenu.authentication",
        icon: "fa-user",
        route:"routeAuthentication"
    }, {
        title: "config.AppConfiguration.Navigation.routeSideMenu.authorization",
        icon: "fa-key",
        route: "routeAuthorization"
    }, {
        title: "config.AppConfiguration.Navigation.routeSideMenu.statistics",
        icon: "fa-line-chart",
        route: "routeStatistics"
    }];

    const RoutesTreeNavigationView = TreeNavigation.extend({
        events: {
            "click [data-event]": "sendEvent",
            "click .route-export": "exportRouteConfig",
            "click .route-duplicate": "duplicateRoute",
            "click .route-deploy": "deployRoute",
            "click .route-undeploy": "undeployRoute",
            "click .route-delete": "deleteRoute"
        },
        sendEvent (e) {
            e.preventDefault();
            EventManager.sendEvent($(e.currentTarget).data().event, this.data.routePath);
        },

        render (args, callback) {
            this.data.routePath = Router.getCurrentHash().match(Router.currentRoute.url)[1];

            RoutesCollection.byRouteId(this.data.routePath).then(_.bind(function (routeData) {
                if (routeData) {
                    this.data.routeData = routeData;
                    this.data.routeName = routeData.get("id");
                    this.data.deployed = ServerRoutesCollection.isDeployed(this.data.routeName);
                    this.data.deployedDate = routeData.get("deployedDate");
                    this.data.pendingChanges = routeData.get("pendingChanges");
                    this.data.allowDeploy = !this.data.deployed || this.data.pendingChanges;
                    this.data.treeNavigation = createTreeNavigation(navData,
                        [encodeURIComponent(this.data.routePath)]
                    );
                    this.data.title = routeData.get("name");
                    this.data.home = `#${Router.getLink(
                        Router.configuration.routes.routeOverview,
                        [encodeURIComponent(this.data.routePath)])}`;

                    TreeNavigation.prototype.render.call(this, args, callback);
                }
            }, this));
        },
        exportRouteConfig (e) {
            e.preventDefault();
            RoutesUtils.exportRouteConfigDialog(this.data.routeName, this.data.title);
        },

        duplicateRoute (e) {
            e.preventDefault();
            RoutesUtils.duplicateRouteDialog(this.data.routeName, this.data.title);
        },

        deployRoute (e) {
            e.preventDefault();
            RoutesUtils.deployRouteDialog(this.data.routeName, this.data.title).done(() => {
                this.render();
            });
        },

        undeployRoute (e) {
            e.preventDefault();
            RoutesUtils.undeployRouteDialog(this.data.routeName, this.data.title).done(() => {
                this.render();
            });
        },

        deleteRoute (e) {
            e.preventDefault();
            RoutesUtils.deleteRouteDialog(this.data.appName, this.data.title)
                .then(
                    () => {
                        EventManager.sendEvent(
                                Constants.EVENT_CHANGE_VIEW,
                                { route: Router.configuration.routes.listRoutesView }
                       );
                    }
                );
        }
    });

    return new RoutesTreeNavigationView();
});
