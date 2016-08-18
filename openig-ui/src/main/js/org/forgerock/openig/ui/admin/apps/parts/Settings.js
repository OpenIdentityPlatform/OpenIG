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
    "org/forgerock/openig/ui/admin/apps/AbstractAppView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/delegates/AppDelegate",
    "org/forgerock/openig/ui/admin/util/AppsUtils",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/models/AppModel",
    "org/forgerock/openig/ui/admin/models/AppsCollection"
], (
    $,
    _,
    form2js,
    AbstractAppView,
    eventManager,
    validatorsManager,
    constants,
    AppDelegate,
    appsUtils,
    router,
    AppModel,
    AppsCollection
) => (
    AbstractAppView.extend({
        element: ".main",
        template: "templates/openig/admin/apps/parts/Settings.html",
        events: {
            "click #submitApp": "appFormSubmit",
            "click #cancelApp": "appFormCancel",
            "blur #appName": "validateName",
            "onValidate": "onValidate"
        },
        formMode: { ADD:0, DUPLICATE: 1, EDIT: 2 },
        data: {
        },
        app: null,
        render (args, callback) {
            this.data = {};
            this.data.appId = args[0];
            this.data.docHelpUrl = constants.DOC_URL;

            // editState true for readonly
            this.data.editState = false;

            this.data.mode = this.getFormMode();

            if (this.data.mode === this.formMode.EDIT) {
                this.data.pageTitle = $.t("templates.apps.editAppDetails");
                this.data.saveBtnTitle = $.t("common.form.save");
                this.data.cancelBtnTitle = $.t("common.form.reset");
            } else {
                this.data.pageTitle = $.t("templates.apps.addAppTitle");
                this.data.saveBtnTitle = $.t("templates.apps.addAppButton");
                this.data.cancelBtnTitle = $.t("common.form.cancel");
            }

            this.app = this.setupApp(this.data.mode, this.data.appId);
            this.data.appName = this.app.get("content/name");
            this.data.appUrl = this.app.get("content/url");
            this.data.appCondition = this.app.get("content/condition");

            this.renderForm(callback);
        },

        getFormMode () {
            if (router.getURIFragment().match(router.configuration.routes.duplicateAppView.url)) {
                return this.formMode.DUPLICATE;
            } else if (router.getURIFragment().match(router.configuration.routes.appsSettings.url)) {
                return this.formMode.EDIT;
            } else {
                return this.formMode.ADD;
            }
        },

        setupApp (mode, appId) {
            const app = new AppModel();
            switch (mode) {
                case this.formMode.DUPLICATE:
                    AppsCollection.byId(appId).then((parentApp) => {
                        app.set("content", _.clone(parentApp.get("content")));
                    });
                    break;
                case this.formMode.EDIT:
                    AppsCollection.byId(appId).then((parentApp) => {
                        app.set("content", _.clone(parentApp.get("content")));
                        app.set("_id", appId);
                    });
                    break;
            }
            return app;
        },

        renderForm (callback) {
            this.parentRender(() => {
                validatorsManager.bindValidators(this.$el);
                this.loadAppTemplate(callback);
                this.validateName();
            });
        },

        appFormSubmit (event) {
            event.preventDefault();
            const modifiedApp = this.app;
            if (modifiedApp && modifiedApp !== null) {
                this.fillAppFromFormData();
                if (!modifiedApp.isValid()) {
                    const form = this.$el.find("#appForm")[0];
                    $(form).find("input").trigger("validate");
                    return;
                }

                appsUtils.checkName(modifiedApp).then((checkResult) => {
                    if (checkResult !== true) {
                        this.validateName();
                        return;
                    }

                    if (this.data.mode === this.formMode.ADD || this.data.mode === this.formMode.DUPLICATE) {
                        modifiedApp.save();
                        AppsCollection.add([
                            modifiedApp
                        ]);
                    } else {
                        AppsCollection.byId(this.data.appId).then((parentApp) => {
                            parentApp.set("content", modifiedApp.get("content"));
                            parentApp.save();
                        });
                    }
                    eventManager.sendEvent(constants.EVENT_CHANGE_VIEW, {
                        route: router.configuration.routes.appsOverview, args: [this.data.appId]
                    });
                });
            }
        },

        appFormCancel () {
            if (this.data.mode === this.formMode.EDIT) {
                eventManager.sendEvent(constants.EVENT_CHANGE_VIEW, {
                    route: router.configuration.routes.appsSettings, args: [this.data.appId]
                });
            } else {
                eventManager.sendEvent(constants.EVENT_CHANGE_VIEW, {
                    route: router.configuration.routes.appsPage
                });
            }
        },

        fillAppFromFormData () {
            const form = this.$el.find("#appForm")[0];
            const formVal = form2js(form, ".", true);
            // Create simple content + fake id
            if ((this.data.mode === this.formMode.ADD || this.data.mode === this.formMode.DUPLICATE) &&
                !this.app.get("_id")) {
                this.data.appId = formVal.name + Date.now();
                this.app.set("_id", this.data.appId);
            }

            _.extend(formVal, { id: this.data.appId });
            let updatedContent = _.clone(this.app.get("content"));
            updatedContent = _.extend(updatedContent, formVal);
            this.app.set("content", updatedContent);
        },

        validateName () {
            const promise = $.Deferred();
            this.fillAppFromFormData();
            appsUtils.checkName(this.app).then((checkResult) => {
                if (checkResult !== true) {
                    this.$el.find("#appErrorMessage .message").html($.t(checkResult));
                    this.$el.find("#appErrorMessage").show();
                    this.$el.find("#submitApp").prop("disabled", true);
                    promise.resolve(checkResult);
                } else {
                    this.$el.find("#appErrorMessage").hide();
                    this.$el.find("#submitApp").prop("disabled", false);

                    promise.resolve(true);
                }
            });
            return promise;
        }

    })
));
