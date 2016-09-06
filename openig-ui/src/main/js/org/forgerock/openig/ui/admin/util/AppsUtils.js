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
    "org/forgerock/openig/ui/admin/delegates/AppDelegate",
    "org/forgerock/openig/ui/admin/util/AppsUtils",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/openig/ui/admin/models/AppsCollection",
    "org/forgerock/commons/ui/common/components/BootstrapDialogView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/services/TransformService",
    "org/forgerock/commons/ui/common/main/Router"
], (
    $,
    _,
    AppDelegate,
    appsUtils,
    UIUtils,
    AppsCollection,
    BootstrapDialogView,
    eventManager,
    constants,
    transformService,
    router
) => ({
    cleanAppNamen (name) {
        // TODO: add some checks, fixes
        const clearName = name;
        return clearName;
    },

    checkName (app) {
        const promise = $.Deferred();
        AppsCollection.availableApps().then((apps) => {
            const foundApp = _.find(apps.models, (a) => (
                a.get("content/name") === app.get("content/name")) &&
                a.get("_id") !== app.get("_id")
            );

            if (foundApp) {
                promise.resolve("templates.apps.duplicateNameError");
            } else {
                promise.resolve(true);
            }
        });
        return promise;
    },

    toggleValue (e) {
        const toggle = this.$el.find(e.target);
        if (toggle.val() === "true") {
            toggle.val(false);
        } else {
            toggle.val(true);
        }
    },

    duplicateAppDlg (appId, appTitle) {
        UIUtils.confirmDialog($.t("templates.apps.duplicateDialog", { title: appTitle }), "danger",
            () => {
                router.navigate(`apps/duplicate/${appId}`, true);
            }
        );
    },

    exportConfigDlg (appId) {
        // TODO: call export function
        AppsCollection.byId(appId).then((appData) => {
            if (appData) {
                try {
                    const modal = new BootstrapDialogView();
                    modal.contentTemplate = "templates/openig/admin/modals/ModalMessageTemplate.html";
                    modal.data.jsonContent = JSON.stringify(transformService.transformApplication(appData), null, 2);
                    modal.closeByBackdrop = false;
                    modal.draggable = true;
                    modal.setTitle($.t("common.modalWindow.title.configExport"));
                    modal.loadContent().then(() => {
                        modal.show();
                    });
                } catch (e) {
                    eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, {
                        key: e.errorType, filter: e.message
                    });
                }
            }
        });
    },

    deployApplicationDlg (appId, appTitle) {
        UIUtils.confirmDialog($.t("templates.apps.deployDialog", { title: appTitle }), "danger",
            () => {
                AppsCollection.byId(appId).then((appData) => {
                    if (appData) {
                        try {
                            transformService.transformApplication(appData);
                            // TODO: send real config to router endpoint
                        } catch (e) {
                            eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, {
                                key: e.errorType, filter: e.message
                            });
                        }
                    }
                });
            }
        );
    },

    undeployApplicationDlg (appId, appTitle) {
        UIUtils.confirmDialog($.t("templates.apps.undeployDialog", { title: appTitle }), "danger",
            () => {
                // TODO: undeploy
            }
        );
    },

    deleteApplicationDlg (appId, appTitle, deletedCallback) {
        UIUtils.confirmDialog($.t("templates.apps.deleteDialog", { title: appTitle }), "danger",
            () => {
                AppsCollection.removeById(appId);

                eventManager.sendEvent(
                    constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                    { key: "deleteAppSuccess", title: appTitle }
                );

                if (deletedCallback) {
                    deletedCallback();
                }
            }
        );
    },

    addFilterIntoModel (appModel, filter) {
        const content = _.clone(appModel.get("content"));
        const filters = content.filters;
        const orderedFilters = [];
        const newFilterOrder = constants.defaultFiltersOrder[filter.type];
        let filterIncluded = false;
        _.forEach(filters, (f) => {
            let order = constants.defaultFiltersOrder[_.get(f, "type", "Unknown")];
            if (order === undefined) {
                order = constants.defaultFiltersOrder.Unknown;
            }
            if (order > newFilterOrder && !filterIncluded) {
                orderedFilters.push(filter);
                filterIncluded = true;
            }
            orderedFilters.push(f);
        });
        if (!filterIncluded) {
            orderedFilters.push(filter);
        }
        content.filters = orderedFilters;
        appModel.set("content", content);
    }
})
);
