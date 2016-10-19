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
    "i18next",
    "org/forgerock/openig/ui/admin/apps/AbstractAppView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/delegates/AppDelegate",
    "org/forgerock/openig/ui/admin/util/AppsUtils",
    "org/forgerock/openig/ui/admin/util/FormUtils",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/models/AppModel",
    "org/forgerock/openig/ui/admin/models/AppsCollection"
], (
    $,
    _,
    form2js,
    i18n,
    AbstractAppView,
    eventManager,
    validatorsManager,
    constants,
    AppDelegate,
    appsUtils,
    FormUtils,
    router,
    AppModel,
    AppsCollection
) => (
    AbstractAppView.extend({
        element: ".main",
        template: "templates/openig/admin/apps/parts/Settings.html",
        partials: [
            "templates/openig/admin/common/form/EditControl.html"
        ],
        events: {
            "click #submitApp": "appFormSubmit",
            "click #cancelApp": "appFormCancel",
            "blur input[name='name']": "validateName",
            "onValidate": "onValidate",
            "keyup input[name='name']": "generateId",
            "keyup input[name='id']": "validateId"
        },
        formMode: { ADD:0, DUPLICATE: 1, EDIT: 2 },
        data: {
        },
        app: null,
        manualIdChange: false,
        render (args, callback) {
            this.data = {};
            this.data.appId = args[0];
            this.data.docHelpUrl = constants.DOC_URL;

            this.data.mode = this.getFormMode();

            if (this.data.mode === this.formMode.EDIT) {
                this.data.pageTitle = i18n.t("templates.apps.parts.settings.editTitle");
                this.data.saveBtnTitle = i18n.t("common.form.save");
                this.data.cancelBtnTitle = i18n.t("common.form.reset");
            } else {
                this.data.pageTitle = i18n.t("templates.apps.parts.settings.addTitle");
                this.data.saveBtnTitle = i18n.t("templates.apps.parts.settings.addButton");
                this.data.cancelBtnTitle = i18n.t("common.form.cancel");
            }

            this.app = this.setupApp(this.data.mode, this.data.appId);
            this.data.controls = [
                {
                    name: "name",
                    value: this.app.get("name"),
                    validator: "required spaceCheck customValidator"
                },
                {
                    name: "id",
                    value: this.app.get("id"),
                    validator: "required spaceCheck urlCompatible customValidator",
                    disabled: this.data.mode === this.formMode.EDIT
                },
                {
                    name: "baseURI",
                    value: this.app.get("baseURI"),
                    validator: "required baseURI spaceCheck"
                },
                {
                    name: "condition",
                    value: this.app.get("condition"),
                    placeholder: "templates.apps.parts.settings.fields.conditionPlaceHolder",
                    validator: "required spaceCheck"
                }
            ];

            FormUtils.extendControlsSettings(this.data.controls, {
                autoTitle: true,
                autoHint: true,
                translatePath: "templates.apps.parts.settings.fields",
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);

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
            let app = new AppModel();
            switch (mode) {
                case this.formMode.DUPLICATE:
                    AppsCollection.byAppId(appId)
                        .then((parentApp) => {
                            app = parentApp.clone();
                            app.unset("_id");
                        });
                    break;
                case this.formMode.EDIT:
                    AppsCollection.byAppId(appId)
                        .then((parentApp) => {
                            app = parentApp.clone();
                        });
                    break;
            }
            return app;
        },

        renderForm (callback) {
            this.parentRender(() => {
                validatorsManager.bindValidators(this.$el);
                this.loadAppTemplate(callback);
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
                if (this.data.mode === this.formMode.ADD || this.data.mode === this.formMode.DUPLICATE) {
                    modifiedApp.save()
                        .then(
                            (newApp) => {
                                AppsCollection.add([
                                    newApp
                                ]);
                                eventManager.sendEvent(constants.EVENT_CHANGE_VIEW, {
                                    route: router.configuration.routes.appsOverview, args: [this.app.get("id")]
                                });
                            },
                            () => {
                                eventManager.sendEvent(
                                    constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                                    {
                                        key: "appCreationFailed"
                                    }
                                );
                            }
                        );
                } else {
                    AppsCollection.byAppId(this.data.appId)
                        .then(
                            (parentApp) => {
                                parentApp.set(modifiedApp.toJSON());
                                parentApp.save();
                            },
                            () => {
                                eventManager.sendEvent(
                                    constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                                    {
                                        key: "settingsFailed"
                                    }
                                );
                            }
                        );
                }
            }
        },

        appFormCancel () {
            if (this.data.mode === this.formMode.EDIT) {
                eventManager.sendEvent(constants.EVENT_CHANGE_VIEW, {
                    route: router.configuration.routes.appsSettings, args: [this.app.id]
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
            this.app.set(formVal);
        },

        generateId (evt) {
            // Avoid re-generate on tab, after manual change or at edit page
            if (evt.keyCode === 9 || this.manualIdChange || this.data.mode === this.formMode.EDIT) {
                return;
            }
            this.$el.find("[name='id']").val(appsUtils.generateAppId(evt.target.value));
        },

        validateId (evt) {
            if (this.app.id !== evt.target.value) {
                appsUtils.isAppIdUniq(evt.target.value)
                    .then((isValid) => {
                        $(evt.target).data("custom-valid-msg", (isValid ? "" : "templates.apps.duplicateIdError"));
                    });
            }
            if (evt.keyCode !== 9) {
                this.manualIdChange = true;
            }
        },

        validateName (evt) {
            if (this.app.get("name") !== evt.target.value) {
                appsUtils.checkName(evt.target.value)
                    .then((checkResult) => {
                        $(evt.target).data("custom-valid-msg", checkResult || "");
                    });
            }
        }
    })
));
