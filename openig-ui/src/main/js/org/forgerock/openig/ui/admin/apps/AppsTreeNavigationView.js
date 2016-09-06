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
    "org/forgerock/openig/ui/admin/models/AppsCollection",
    "org/forgerock/openig/ui/admin/models/RoutesCollection",
    "org/forgerock/openig/ui/admin/util/AppsUtils"
], ($, _, Constants, EventManager, Router,
    TreeNavigation,
    createTreeNavigation,
    AppsCollection,
    RoutesCollection,
    appsUtils
) => {
    const navData = [{
        title: "config.AppConfiguration.Navigation.appsSideMenu.overview",
        icon: "fa-home",
        route: "appsOverview"
    }, {
        title: "config.AppConfiguration.Navigation.appsSideMenu.throttling",
        icon: "fa-filter",
        route:"appsThrottling"
    }, {
        title: "config.AppConfiguration.Navigation.appsSideMenu.authentication",
        icon: "fa-user",
        route:"appsAuthentication"
    }, {
        title: "config.AppConfiguration.Navigation.appsSideMenu.authorization",
        icon: "fa-key",
        route: "appsAuthorization"
    }, {
        title: "config.AppConfiguration.Navigation.appsSideMenu.transformation",
        icon: "fa-random",
        route: "appsTransformation"
    }, {
        title: "config.AppConfiguration.Navigation.appsSideMenu.monitoring",
        icon: "fa-line-chart",
        route: "appsMonitoring"
    }];

    const AppsTreeNavigationView = TreeNavigation.extend({
        events: {
            "click [data-event]": "sendEvent",
            "click .app-export": "exportAppConfig",
            "click .app-duplicate": "duplicateAppConfig",
            "click .app-deploy": "deployApp",
            "click .app-undeploy": "undeployApp",
            "click .app-delete": "deleteApps"
        },
        sendEvent (e) {
            e.preventDefault();
            EventManager.sendEvent($(e.currentTarget).data().event, this.data.appPath);
        },

        render (args, callback) {
            this.data.appPath = args[0];

            AppsCollection.byId(this.data.appPath).then(_.bind(function (appData) {
                if (appData) {
                    this.data.appData = appData;
                    this.data.appName = appData.get("_id");
                    this.data.deployed = RoutesCollection.isDeployed(this.data.appName);
                    this.data.deployedDate = appData.get("content/deployedDate");
                    this.data.pendingChanges = appData.get("content/pendingChanges");
                    this.data.allowDeploy = !this.data.deployed || this.data.pendingChanges;
                    this.data.treeNavigation = createTreeNavigation(navData, [encodeURIComponent(this.data.appPath)]);
                    this.data.title = appData.get("content/name");
                    this.data.home = `#${Router.getLink(
                        Router.configuration.routes.appsOverview,
                        [encodeURIComponent(this.data.appPath)])}`;

                    TreeNavigation.prototype.render.call(this, args, callback);
                }
            }, this));
        },
        exportAppConfig (e) {
            e.preventDefault();
            appsUtils.exportConfigDlg(this.data.appName, this.data.title);
        },

        duplicateAppConfig (e) {
            e.preventDefault();
            appsUtils.duplicateAppDlg(this.data.appName, this.data.title);
        },

        deployApp (e) {
            e.preventDefault();
            appsUtils.deployApplicationDlg(this.data.appName, this.data.title);
        },

        undeployApplication (e) {
            e.preventDefault();
            appsUtils.deployApplicationDlg(this.data.appName, this.data.title);
        },

        deleteApps (e) {
            e.preventDefault();
            appsUtils.deleteApplicationDlg(this.data.appName, this.data.title,
                () => {
                    EventManager.sendEvent(
                            Constants.EVENT_CHANGE_VIEW,
                            { route: Router.configuration.routes.appsPage }
                   );
                }
            );
        }
    });

    return new AppsTreeNavigationView();
});
