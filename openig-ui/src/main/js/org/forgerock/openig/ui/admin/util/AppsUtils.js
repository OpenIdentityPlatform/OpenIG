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
    "i18next",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/openig/ui/admin/models/AppsCollection",
    "org/forgerock/openig/ui/admin/models/RoutesCollection",
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
    AppsCollection,
    RoutesCollection,
    BootstrapDialog,
    eventManager,
    constants,
    transformService,
    router,
    Clipboard,
    CodeMirror
) => ({
    generateAppId (appName) {
        return appName.toLowerCase()
            .replace(/[^\w ]+/g, "")
            .replace(/ +/g, "-");
    },

    isAppIdUniq (appId) {
        return AppsCollection.byId(appId)
            .then((appModel) => !appModel);
    },

    checkName (appName) {
        return AppsCollection.availableApps().then((apps) => {
            const foundApp = _.find(apps.models, (a) => (
                a.get("content/name") === appName
            ));
            return foundApp ? "templates.apps.duplicateNameError" : "";
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

    duplicateAppDlg (appId, appTitle) {
        UIUtils.confirmDialog(i18n.t("templates.apps.duplicateDialog", { title: appTitle }), "danger",
            () => {
                router.navigate(`apps/duplicate/${appId}`, true);
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

    exportConfigDlg (appId) {
        AppsCollection.byId(appId).then((appData) => {
            if (appData) {
                try {
                    const jsonContent = JSON.stringify(transformService.transformApplication(appData), null, 2);
                    this.showExportDialog(jsonContent);
                } catch (e) {
                    eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, {
                        key: e.errorType || "modelTransformationFailed", message: e.message
                    });
                }
            }
        });
    },

    deployApplicationModel (model) {
        const deferred = $.Deferred();
        const promise = deferred.promise();
        const appId = model.get("_id");
        const appTitle = model.get("content/name");
        const jsonConfig = transformService.transformApplication(model);
        RoutesCollection.deploy(appId, jsonConfig).done(() => {
            eventManager.sendEvent(
                constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                { key: "appDeployedSuccess", title: appTitle }
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
                { key: "appDeployedFailed", title: appTitle, message: errorMessage }
            );
            deferred.reject();
        });
        return promise;
    },

    deployApplicationDlg (appId, appTitle) {
        const deferred = $.Deferred();
        const promise = deferred.promise();
        AppsCollection.byId(appId).then((appData) => {
            if (appData) {
                const isDeployed = RoutesCollection.isDeployed(appId);
                if (!isDeployed) {
                    this.deployApplicationModel(appData).done(() => {
                        deferred.resolve();
                    }).fail(() => {
                        deferred.reject();
                    });
                } else {
                    UIUtils.confirmDialog(i18n.t("templates.apps.deployDialog", { title: appTitle }), "danger",
                        () => {
                            this.deployApplicationModel(appData).done(() => {
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

    undeployApplication (appId) {
        const deferred = $.Deferred();
        RoutesCollection.undeploy(appId).done(() => {
            AppsCollection.byId(appId).then((appData) => {
                appData.set("deployedDate", null);
                appData.set("pendingChanges", false);
                appData.save();
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

    undeployApplicationDlg (appId, appTitle) {
        const deferred = $.Deferred();
        const promise = deferred.promise();
        UIUtils.confirmDialog(i18n.t("templates.apps.undeployDialog", { title: appTitle }), "danger",
            () => {
                this.undeployApplication(appId)
                    .then(
                        () => {
                            eventManager.sendEvent(
                                constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                                { key: "appUndeployedSuccess", title: appTitle }
                            );
                            deferred.resolve();
                        },
                        (errorMessage) => {
                            eventManager.sendEvent(
                                constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                                { key: "appUndeployedFailed", title: appTitle, message: errorMessage }
                            );
                            deferred.reject();
                        }
                    );
            }
        );
        return promise;
    },

    deleteApplication (appId, appTitle) {
        const deferred = $.Deferred();
        AppsCollection.removeById(appId)
            .then(
                () => {
                    eventManager.sendEvent(
                        constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                        { key: "deleteAppSuccess", title: appTitle }
                    );
                    deferred.resolve();
                },
                () => {
                    eventManager.sendEvent(
                        constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                        { key: "deleteAppFailed", title: appTitle }
                    );
                    deferred.reject();
                }
            );
        return deferred;
    },

    deleteApplicationDlg (appId, appTitle) {
        const deferred = $.Deferred();
        AppsCollection.byId(appId).then((appModel) => {
            if (appModel) {
                const isDeployed = RoutesCollection.isDeployed(appId);
                const dialogMessageKey =
                    isDeployed ? "templates.apps.undeployAndDeleteDialog" : "templates.apps.deleteDialog";
                UIUtils.confirmDialog(i18n.t(dialogMessageKey, { title: appTitle }), "danger",
                    () => {
                        if (isDeployed) {
                            this.undeployApplication(appId)
                                .then(() => {
                                    this.deleteApplication(appId, appTitle)
                                        .then(
                                            () => { deferred.resolve(); },
                                            () => { deferred.reject(); }
                                        );
                                },
                                (errorMessage) => {
                                    eventManager.sendEvent(
                                        constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                                        { key: "appUndeployedFailed", title: appTitle, message: errorMessage }
                                    );
                                    deferred.reject();
                                }
                            );
                        } else {
                            this.deleteApplication(appId, appTitle)
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

    addFilterIntoModel (appModel, filter) {
        const content = _.clone(appModel.get("content"));
        const filters = content.filters;

        if (_.includes(filters, filter)) {
            return;
        }
        filters.push(filter);
        content.filters = _.sortBy(filters, (f) =>
            constants.defaultFiltersOrder[_.get(f, "type", "Unknown")]
        );
        appModel.set("content", content);
    }
})
);
