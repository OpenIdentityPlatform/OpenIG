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
    "org/forgerock/openig/ui/admin/apps/AbstractAppView",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/delegates/AppDelegate",
    "org/forgerock/openig/ui/admin/util/AppsUtils",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/common/delegates/ConfigDelegate",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/commons/ui/common/util/ModuleLoader",
    "org/forgerock/openig/ui/admin/models/AppsCollection"
], function (
    $,
    _,
    AbstractAppView,
    validatorsManager,
    constants,
    AppDelegate,
    appsUtils,
    router,
    ConfigDelegate,
    UIUtils,
    ModuleLoader,
    AppsCollection
) {
    var AddEditAppView = AbstractAppView.extend({
        template: "templates/openig/admin/apps/EditAppTemplate.html",
        events: {
            "click .appDeploy": "appDeploy",
            "click .appUndeploy": "appUndeploy",
            "click .btnAppSettings": "loadAppPartSettings",
            "click .exportConfig": "exportConfig",
            "click .appDelete": "appDelete"
        },
        data: {

        },

        render: function (args) {
            var appId,
                appPartViewId;

            this.data = {};
            this.data.docHelpUrl = constants.DOC_URL;
            this.postActionBlockScript = null;
            this.name = null;
            this.addedLiveSyncSchedules = [];

            // Commented lines are not part of MVP
            this.data.sideNav = [
                { id: "app-overview", icon: "fa fa-home", title: "Overview", part: "Overview" },
                { id: "app-throttling", icon: "fa fa-filter", title: "Throttling", part: "Throttling" },
                {
                    id: "app-authentication", icon: "fa fa-check-square-o", title: "Authentication",
                    part: "Authentication"
                },
                //{id:"app-authorization", icon: "fa fa-key", title: "Authorization", part:"Authorization"},
                { id: "app-transformation", icon: "fa fa-random", title: "Transformation", part: "Transformation" },
                //{id:"app-scripts", icon: "fa fa-code", title: "Scripts", part:"Script"},
                //{id:"app-chain", icon: "fa fa-link", title: "Chain", part:"Chain"},
                //{id:"app-debug", icon: "fa fa-bug", title: "Debug", part:"Debug"},
                { id: "app-monitoring", icon: "fa fa-line-chart", title: "Monitoring", part: "Monitoring" }
                //{id:"app-audit", icon: "fa fa-search", title: "Audit", part:"Audit"}
            ];

            if (args) {
                appId = args[0];
                appPartViewId = args[1];

                // TODO: Call query with id
                AppsCollection.byId(appId).then(_.bind(function (appData) {
                    if (appData) {
                        appData.cleanUrlName = appsUtils.cleanAppName(appData.get("_id"));
                        appData.cleanEditName = appsUtils.cleanAppName(appData.get("_id"));
                        // TODO: deployed state from Router
                        //appData.deployed = appData.attributes.content.status === 'deployed';
                        this.data.currentApp = appData.attributes;

                        //TEMP
                        this.parentRender(_.bind(function () {
                            this.loadAppPartTemplateByName(appPartViewId);
                        }, this));
                    }
                }, this));
            }
        },

        loadAppPartTemplateByName: function (partname) {
            var navItem, templateName;
            partname = partname.toLowerCase();
            navItem = _.find(this.data.sideNav, function (navItem) { return navItem.part.toLowerCase() === partname; });
            if (navItem) {
                // TODO: remove magic constants
                templateName = "org/forgerock/openig/ui/admin/apps/parts/" + navItem.part;
                ModuleLoader.load(templateName).then(_.bind(function (tempForm) {
                    tempForm.render();
                }));
            }
            return false;
        },

        appDeploy: function () {
            UIUtils.confirmDialog($.t("Deploy application ?"), "danger", _.bind(function () {

            }));
        },

        appUndeploy: function () {
            UIUtils.confirmDialog($.t("Undeploy application ?"), "danger", _.bind(function () {

            }));
        },

        exportConfig: function () {
            UIUtils.confirmDialog($.t("Export config?"), "danger", _.bind(function () {

            }));
        },

        appDelete: function () {
            UIUtils.confirmDialog($.t("Delete application ?"), "danger", _.bind(function () {

            }));
        }
    });
    return new AddEditAppView();
});
