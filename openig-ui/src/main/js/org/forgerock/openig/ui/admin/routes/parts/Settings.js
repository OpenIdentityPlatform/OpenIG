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
    "org/forgerock/openig/ui/admin/routes/AbstractRouteView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/delegates/AppDelegate",
    "org/forgerock/openig/ui/admin/util/RoutesUtils",
    "org/forgerock/openig/ui/admin/util/FormUtils",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/models/RouteModel",
    "org/forgerock/openig/ui/admin/models/RoutesCollection"
], (
    $,
    _,
    form2js,
    i18n,
    AbstractRouteView,
    eventManager,
    validatorsManager,
    constants,
    AppDelegate,
    RoutesUtils,
    FormUtils,
    router,
    RouteModel,
    RoutesCollection
) => (
    AbstractRouteView.extend({
        element: ".main",
        template: "templates/openig/admin/routes/parts/Settings.html",
        partials: [
            "templates/openig/admin/common/form/EditControl.html"
        ],
        events: {
            "click #submitRoute": "routeFormSubmit",
            "click #cancelRoute": "routeFormCancel",
            "blur input[name='name']": "validateName",
            "onValidate": "onValidate",
            "keyup input[name='name']": "generateId",
            "keyup input[name='id']": "validateId"
        },
        formMode: { ADD:0, DUPLICATE: 1, EDIT: 2 },
        data: {
        },
        routeModel: null,
        manualIdChange: false,
        render (args, callback) {
            this.data = {};
            this.data.routeId = args[0];
            this.data.docHelpUrl = constants.DOC_URL;

            this.data.mode = this.getFormMode();

            if (this.data.mode === this.formMode.EDIT) {
                this.data.pageTitle = i18n.t("templates.routes.parts.settings.editTitle");
                this.data.saveBtnTitle = i18n.t("common.form.save");
                this.data.cancelBtnTitle = i18n.t("common.form.reset");
            } else {
                this.data.pageTitle = i18n.t("templates.routes.parts.settings.addTitle");
                this.data.saveBtnTitle = i18n.t("templates.routes.parts.settings.addButton");
                this.data.cancelBtnTitle = i18n.t("common.form.cancel");
            }

            this.routeModel = this.setupRoute(this.data.mode, this.data.routeId);
            this.data.controls = [
                {
                    name: "name",
                    value: this.routeModel.get("name"),
                    validator: "required spaceCheck customValidator"
                },
                {
                    name: "id",
                    value: this.routeModel.get("id"),
                    validator: "required spaceCheck urlCompatible customValidator",
                    disabled: this.data.mode === this.formMode.EDIT
                },
                {
                    name: "baseURI",
                    value: this.routeModel.get("baseURI"),
                    validator: "required baseURI spaceCheck"
                },
                {
                    name: "condition",
                    value: this.routeModel.get("condition"),
                    placeholder: "templates.routes.parts.settings.fields.conditionPlaceHolder",
                    validator: "required spaceCheck"
                }
            ];

            FormUtils.extendControlsSettings(this.data.controls, {
                autoTitle: true,
                autoHint: true,
                translatePath: "templates.routes.parts.settings.fields",
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);

            this.renderForm(callback);
        },

        getFormMode () {
            if (router.getURIFragment().match(router.configuration.routes.duplicateRouteView.url)) {
                return this.formMode.DUPLICATE;
            } else if (router.getURIFragment().match(router.configuration.routes.routeSettings.url)) {
                return this.formMode.EDIT;
            } else {
                return this.formMode.ADD;
            }
        },

        setupRoute (mode, routeId) {
            let route = new RouteModel();
            switch (mode) {
                case this.formMode.DUPLICATE:
                    RoutesCollection.byRouteId(routeId)
                        .then((parentRoute) => {
                            route = parentRoute.clone();
                            route.unset("_id");
                        });
                    break;
                case this.formMode.EDIT:
                    RoutesCollection.byRouteId(routeId)
                        .then((parentRoute) => {
                            route = parentRoute.clone();
                        });
                    break;
            }
            return route;
        },

        renderForm (callback) {
            this.parentRender(() => {
                validatorsManager.bindValidators(this.$el);
                this.loadRouteTemplate(callback);
            });
        },

        routeFormSubmit (event) {
            event.preventDefault();
            const modifiedRoute = this.routeModel;
            if (modifiedRoute && modifiedRoute !== null) {
                this.fillRouteFromFormData();
                if (!modifiedRoute.isValid()) {
                    const form = this.$el.find("#routeForm")[0];
                    $(form).find("input").trigger("validate");
                    return;
                }
                if (this.data.mode === this.formMode.ADD || this.data.mode === this.formMode.DUPLICATE) {
                    modifiedRoute.save()
                        .then(
                            (newRoute) => {
                                RoutesCollection.add([
                                    newRoute
                                ]);
                                eventManager.sendEvent(constants.EVENT_CHANGE_VIEW, {
                                    route: router.configuration.routes.routeOverview, args: [this.routeModel.get("id")]
                                });
                            },
                            () => {
                                eventManager.sendEvent(
                                    constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                                    {
                                        key: "routeCreationFailed"
                                    }
                                );
                            }
                        );
                } else {
                    RoutesCollection.byRouteId(this.data.routeId)
                        .then(
                            (parentRoute) => {
                                parentRoute.set(modifiedRoute.toJSON());
                                parentRoute.save();
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

        routeFormCancel () {
            if (this.data.mode === this.formMode.EDIT) {
                eventManager.sendEvent(constants.EVENT_CHANGE_VIEW, {
                    route: router.configuration.routes.routeSettings, args: [this.routeModel.id]
                });
            } else {
                eventManager.sendEvent(constants.EVENT_CHANGE_VIEW, {
                    route: router.configuration.routes.listRoutesView
                });
            }
        },

        fillRouteFromFormData () {
            const form = this.$el.find("#routeForm")[0];
            const formVal = form2js(form, ".", true);
            this.routeModel.set(formVal);
        },

        generateId (evt) {
            // Avoid re-generate on tab, after manual change or at edit page
            if (evt.keyCode === 9 || this.manualIdChange || this.data.mode === this.formMode.EDIT) {
                return;
            }
            this.$el.find("[name='id']").val(RoutesUtils.generateRouteId(evt.target.value));
        },

        validateId (evt) {
            if (this.routeModel.id !== evt.target.value) {
                RoutesUtils.isRouteIdUniq(evt.target.value)
                    .then((isValid) => {
                        $(evt.target).data("custom-valid-msg", (isValid ? "" : "templates.routes.duplicateIdError"));
                    });
            }
            if (evt.keyCode !== 9) {
                this.manualIdChange = true;
            }
        },

        validateName (evt) {
            if (this.routeModel.get("name") !== evt.target.value) {
                RoutesUtils.checkName(evt.target.value)
                    .then((checkResult) => {
                        $(evt.target).data("custom-valid-msg", checkResult || "");
                    });
            }
        }
    })
));
