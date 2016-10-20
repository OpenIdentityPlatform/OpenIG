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
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/openig/ui/admin/util/RoutesUtils",
    "org/forgerock/openig/ui/admin/util/FormUtils",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants"
], (
    $,
    _,
    form2js,
    i18n,
    AbstractView,
    validatorsManager,
    RoutesUtils,
    FormUtils,
    EventManager,
    Constants
    ) => (
    AbstractView.extend({
        element: ".main",
        template: "templates/openig/admin/routes/parts/Authorization.html",
        partials: [
            "templates/openig/admin/common/form/EditControl.html",
            "templates/openig/admin/common/form/SliderControl.html",
            "templates/openig/admin/common/form/GroupControl.html"
        ],
        events: {
            "click input[name='enabled']": "enableAuthorizationClick",
            "click #cancelAuthorization": "cancelClick",
            "click #submitAuthorization": "saveClick",
            "onValidate": "onValidate"
        },
        data: {},
        initialize (options) {
            this.data = options.parentData;
        },
        render () {
            this.data.newFilter = false;
            this.data.authZFilter = this.getFilter();
            if (!this.data.authZFilter) {
                this.data.authZFilter = this.createFilter();
                this.data.newFilter = true;
            }

            this.data.controls = [
                {
                    name: "enabled",
                    value:  this.data.authZFilter.enabled,
                    controlType: "slider",
                    hint: false
                },
                {
                    name: "authZGroup",
                    title: "",
                    controlType: "group",
                    cssClass: this.data.authZFilter.enabled ? "collapse in" : "collapse",
                    controls: [
                        {
                            name: "openAMconfigurationGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "openamUrl",
                                    value: this.data.authZFilter.openamUrl,
                                    validator: "uri required"
                                },
                                {
                                    name: "pepUsername",
                                    value: this.data.authZFilter.pepUsername,
                                    validator: "required"
                                },
                                {
                                    name: "pepPassword",
                                    value: this.data.authZFilter.pepPassword,
                                    validator: "required"
                                },
                                {
                                    name: "pepRealm",
                                    value: this.data.authZFilter.pepRealm
                                }
                            ]
                        },
                        {
                            name: "enforcementEndpointGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "realm",
                                    value: this.data.authZFilter.realm
                                },
                                {
                                    name: "ssoTokenSubject",
                                    value: this.data.authZFilter.ssoTokenSubject,
                                    validator: "required"
                                },
                                {
                                    name: "application",
                                    value: this.data.authZFilter.application
                                }
                            ]
                        }
                    ]
                }
            ];
            FormUtils.extendControlsSettings(this.data.controls, {
                autoTitle: true,
                autoHint: true,
                translatePath: "templates.routes.parts.authorization.fields",
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);
            this.parentRender(() => {
                this.setFormFooterVisiblity(this.data.authZFilter.enabled);
                validatorsManager.bindValidators(this.$el);
            });
        },

        enableAuthorizationClick (event) {
            const newState = event.currentTarget.checked;
            const collapseState = newState ? "show" : "hide";
            this.$el.find("div[name='authZGroup']").collapse(collapseState);

            if (!newState) {
                //Save Off state
                this.data.authZFilter.enabled = newState;
                this.data.routeData.save();
            } else {
                //Save On state, only when form is valid
                const form = this.$el.find("#authorizationForm")[0];
                FormUtils.isFormValid(form)
                    .done(
                    () => {
                        this.data.authZFilter.enabled = newState;
                        this.data.routeData.save();
                    });
            }
            this.setFormFooterVisiblity(newState);
        },

        setFormFooterVisiblity (visible) {
            const footerPanel = this.$el.find(".panel-footer");
            if (visible) {
                footerPanel.show();
            } else {
                footerPanel.hide();
            }
        },

        cancelClick () {
            event.preventDefault();
            this.render();
        },

        saveClick () {
            event.preventDefault();
            const form = this.$el.find("#authorizationForm")[0];
            FormUtils.isFormValid(form)
                .done(
                () => {
                    const formVal = form2js(form, ".", false);
                    _.extend(this.data.authZFilter, formVal);
                    this.data.authZFilter.enabled = FormUtils.getBoolValue(formVal.enabled);
                    if (this.data.newFilter) {
                        RoutesUtils.addFilterIntoModel(this.data.routeData, this.data.authZFilter);
                    }
                    this.data.routeData.save();

                    EventManager.sendEvent(
                        Constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                        {
                            key: "routeSettingsSaveSuccess",
                            filter: i18n.t("templates.routes.parts.authorization.title")
                        }
                    );
                })
                .fail(
                () => {
                    $(form).find("input").trigger("validate");
                });
        },

        getFilter () {
            return _.find(this.data.routeData.get("filters"),
                { "type": "PolicyEnforcementFilter" }
            );
        },

        createFilter () {
            return {
                "type": "PolicyEnforcementFilter"
            };
        }
    })
));
