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
    "i18next",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/openig/ui/admin/models/RoutesCollection",
    "org/forgerock/openig/ui/admin/models/ServerRoutesCollection",
    "org/forgerock/commons/ui/common/components/BootstrapDialog",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/services/TransformService",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/common/util/Clipboard",
    "codemirror",
    // codemirror's dependency for ld+json mode
    "codemirror/mode/javascript/javascript"
], (
    $,
    _,
    i18n,
    UIUtils,
    RoutesCollection,
    ServerRoutesCollection,
    BootstrapDialog,
    eventManager,
    constants,
    transformService,
    router,
    Clipboard,
    CodeMirror
) => ({
    generateRouteId (routeName) {
        return routeName.toLowerCase()
            .replace(/[^\w ]+/g, "")
            .replace(/ +/g, "-");
    },

    isRouteIdUniq (routeId) {
        return RoutesCollection.byRouteId(routeId)
            .then((routeModel) => !routeModel);
    },

    checkName (name) {
        return RoutesCollection.availableRoutes().then((routes) => {
            const foundRoute = routes.findWhere({ name });
            return foundRoute ? "templates.routes.duplicateNameError" : "";
        });
    },

    toggleValue (e) {
        const toggle = this.$el.find(e.target);
        if (toggle.val() === "true") {
            toggle.val(false);
        } else {
            toggle.val(true);
        }
    },

    duplicateRoutesDialog (routeId, routeTitle) {
        UIUtils.confirmDialog(i18n.t("templates.routes.duplicateDialog", { title: routeTitle }), "danger",
            () => {
                router.navigate(`routes/duplicate/${routeId}`, true);
            }
        );
    },

    showTooltip (target, options) {
        target.tooltip(_.extend({
            container: "body",
            placement: "bottom",
            trigger: "manual"
        }, options));
        target.tooltip("show");
        _.delay(() => {
            target.tooltip("hide");
        }, 1500);
    },

    showExportDialog (jsonContent) {
        const self = this;
        const buttons = [];
        if (Clipboard.isClipboardEnabled()) {
            buttons.push(
                {
                    id: "btnOk",
                    label: i18n.t("common.modalWindow.button.copyToClipboard"),
                    cssClass: "btn-default",
                    action (dialog) {
                        const copyElement = dialog.getMessage().find("#jsonExportContent")[0];
                        if (Clipboard.copyContent(copyElement)) {
                            self.showTooltip(this, {
                                title: i18n.t("common.modalWindow.message.copied")
                            });
                        } else {
                            self.showTooltip(this, {
                                title: i18n.t("common.modalWindow.message.copyFailed")
                            });
                        }
                    }
                }
            );
        }
        buttons.push(
            {
                label: i18n.t("common.form.cancel"),
                cssClass: "btn-primary",
                action (dialog) {
                    dialog.close();
                }
            }
        );

        const msgNode = $(`<div><pre id="jsonExportContent" class="hidden-pre">${jsonContent}</pre></div>`);
        const codeMirror = CodeMirror((elm) => {
            msgNode.append(elm);
        }, {
            value: jsonContent,
            mode: "application/ld+json",
            theme: "forgerock",
            autofocus: true,
            readOnly: true
        });
        codeMirror.setSize("100%", "100%");
        BootstrapDialog.show({
            title: i18n.t("common.modalWindow.title.configExport"),
            message: msgNode,
            closable: true,
            buttons,
            onshown () {
                this.message.css("max-height", "calc(100vh - 212px)");
                codeMirror.refresh();
                this.message.css("height", this.message.find(".CodeMirror").height());
            }
        });
    },

    exportRouteConfigDialog (id) {
        RoutesCollection.byRouteId(id).then((routeData) => {
            if (routeData) {
                try {
                    const jsonContent = JSON.stringify(transformService.transformRoute(routeData), null, 2);
                    this.showExportDialog(jsonContent);
                } catch (e) {
                    eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, {
                        key: e.errorType || "modelTransformationFailed", message: e.message
                    });
                }
            }
        });
    },

    deployRouteModel (model) {
        const deferred = $.Deferred();
        const promise = deferred.promise();
        const id = model.get("id");
        const title = model.get("name");
        const jsonConfig = transformService.transformRoute(model);
        ServerRoutesCollection.deploy(id, jsonConfig).done(() => {
            eventManager.sendEvent(
                constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                { key: "routeDeployedSuccess", title }
            );
            model.set("deployedDate", new Date());
            model.set("pendingChanges", false);
            model.save();
            deferred.resolve();
        }).fail((errorResponse) => {
            let errorMessage;
            if (errorResponse) {
                errorMessage = errorResponse.cause ? errorResponse.cause.message : errorResponse.statusText;
            }
            eventManager.sendEvent(
                constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                { key: "routeDeployedFailed", title, message: errorMessage }
            );
            deferred.reject();
        });
        return promise;
    },

    deployRouteDialog (id, title) {
        const deferred = $.Deferred();
        const promise = deferred.promise();
        RoutesCollection.byRouteId(id).then((routeData) => {
            if (routeData) {
                const isDeployed = ServerRoutesCollection.isDeployed(id);
                if (!isDeployed) {
                    this.deployRouteModel(routeData).done(() => {
                        deferred.resolve();
                    }).fail(() => {
                        deferred.reject();
                    });
                } else {
                    UIUtils.confirmDialog(i18n.t("templates.routes.deployDialog", { title }), "danger",
                        () => {
                            this.deployRoutesModel(routeData).done(() => {
                                deferred.resolve();
                            }).fail(() => {
                                deferred.reject();
                            });
                        });
                }
            }
        });
        return promise;
    },

    undeployRoute (id) {
        const deferred = $.Deferred();
        ServerRoutesCollection.undeploy(id).done(() => {
            RoutesCollection.byRouteId(id).then((routeData) => {
                routeData.set("deployedDate", null);
                routeData.set("pendingChanges", false);
                routeData.save();
                deferred.resolve();
            });
        }).fail((errorResponse) => {
            let errorMessage;
            if (errorResponse) {
                errorMessage = errorResponse.cause ? errorResponse.cause.message : errorResponse.statusText;
            }
            deferred.reject(errorMessage);
        });
        return deferred;
    },

    undeployRouteDialog (id, title) {
        const deferred = $.Deferred();
        const promise = deferred.promise();
        UIUtils.confirmDialog(i18n.t("templates.routes.undeployDialog", { title }), "danger",
            () => {
                this.undeployRoute(id)
                    .then(
                        () => {
                            eventManager.sendEvent(
                                constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                                { key: "routeUndeployedSuccess", title }
                            );
                            deferred.resolve();
                        },
                        (errorMessage) => {
                            eventManager.sendEvent(
                                constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                                { key: "routeUndeployedFailed", title, message: errorMessage }
                            );
                            deferred.reject();
                        }
                    );
            }
        );
        return promise;
    },

    deleteRoute (id, title) {
        const deferred = $.Deferred();
        RoutesCollection.removeByRouteId(id)
            .then(
                () => {
                    eventManager.sendEvent(
                        constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                        { key: "deleteRouteSuccess", title }
                    );
                    deferred.resolve();
                },
                () => {
                    eventManager.sendEvent(
                        constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                        { key: "deleteRouteFailed", title }
                    );
                    deferred.reject();
                }
            );
        return deferred;
    },

    deleteRouteDialog (id, title) {
        const deferred = $.Deferred();
        RoutesCollection.byRouteId(id).then((routeModel) => {
            if (routeModel) {
                const isDeployed = ServerRoutesCollection.isDeployed(id);
                const dialogMessageKey =
                    isDeployed ? "templates.routes.undeployAndDeleteDialog" : "templates.routes.deleteDialog";
                UIUtils.confirmDialog(i18n.t(dialogMessageKey, { title }), "danger",
                    () => {
                        if (isDeployed) {
                            this.undeployRoute(id)
                                .then(() => {
                                    this.deleteRoute(id, title)
                                        .then(
                                            () => { deferred.resolve(); },
                                            () => { deferred.reject(); }
                                        );
                                },
                                (errorMessage) => {
                                    eventManager.sendEvent(
                                        constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                                        { key: "routeUndeployedFailed", title, message: errorMessage }
                                    );
                                    deferred.reject();
                                }
                            );
                        } else {
                            this.deleteRoute(id, title)
                                .then(
                                    () => { deferred.resolve(); },
                                    () => { deferred.reject(); }
                                );
                        }
                    }
                );
            }
        });
        return deferred;
    },

    addFilterIntoModel (routeModel, filter) {
        let filters = _.clone(routeModel.get("filters"));

        if (_.includes(filters, filter)) {
            return;
        }
        filters.push(filter);
        filters = _.sortBy(filters, (f) =>
            constants.defaultFiltersOrder[_.get(f, "type", "Unknown")]
        );
        routeModel.set("filters", filters);
    }
})
);
