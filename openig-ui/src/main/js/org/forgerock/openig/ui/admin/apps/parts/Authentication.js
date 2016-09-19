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
    "selectize",
    "i18next",
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/openig/ui/admin/util/AppsUtils",
    "org/forgerock/openig/ui/admin/util/FormUtils",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants"
], (
    $,
    _,
    form2js,
    selectize,
    i18n,
    AbstractView,
    validatorsManager,
    AppsUtils,
    FormUtils,
    EventManager,
    Constants
    ) => (
    AbstractView.extend({
        element: ".main",
        template: "templates/openig/admin/apps/parts/Authentication.html",
        partials: [
            "templates/openig/admin/common/form/EditControl.html",
            "templates/openig/admin/common/form/SliderControl.html",
            "templates/openig/admin/common/form/GroupControl.html",
            "templates/openig/admin/common/form/CheckboxControl.html",
            "templates/openig/admin/common/form/MultiSelectControl.html"
        ],
        events: {
            "click input[name='enabled']": "enableAuthenticationClick",
            "click #cancelAuth": "cancelClick",
            "click #submitAuth": "saveClick",
            "onValidate": "onValidate"
        },
        data: {},
        initialize (options) {
            this.data = options.parentData;
        },
        render () {
            this.data.newFilter = false;
            this.data.authFilter = this.getFilter();
            if (!this.data.authFilter) {
                this.data.authFilter = this.createFilter();
                this.data.newFilter = true;
            }

            this.data.controls = [
                {
                    name: "enabled",
                    value:  this.data.authFilter.enabled ? "checked" : "",
                    controlType: "slider",
                    hint: false
                },
                {
                    name: "authGroup",
                    title: "",
                    controlType: "group",
                    cssClass: this.data.authFilter.enabled ? "collapse in" : "collapse",
                    controls: [
                        {
                            name: "clientFilterGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "clientEndpoint",
                                    value: this.data.authFilter.clientEndpoint
                                }
                            ]
                        },
                        {
                            name: "clientRegistrationGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "clientId",
                                    value: this.data.authFilter.clientId,
                                    validator: "required"
                                },
                                {
                                    name: "clientSecret",
                                    value: this.data.authFilter.clientSecret,
                                    validator: "required"
                                },
                                {
                                    name: "scopes",
                                    value: this.data.authFilter.scopes,
                                    controlType: "multiselect",
                                    options: ["openid", "profile", "email", "address", "phone", "offline_access"],
                                    delimiter: " ",
                                    mandatory: "openid"
                                },
                                {
                                    name: "tokenEndpointUseBasicAuth",
                                    value: this.data.authFilter.tokenEndpointUseBasicAuth ? "checked" : "",
                                    controlType: "slider"
                                },
                                {
                                    name: "requireHttps",
                                    value: this.data.authFilter.requireHttps ? "checked" : "",
                                    controlType: "slider"
                                }
                            ]
                        },
                        {
                            title: "Issuer",
                            name: "issuerGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "issuerWellKnownEndpoint",
                                    value: this.data.authFilter.issuerWellKnownEndpoint,
                                    validator: "required uri"
                                }
                            ]
                        }
                    ]
                }
            ];
            FormUtils.extendControlsSettings(this.data.controls, {
                autoTitle: true,
                autoHint: true,
                translatePath: "templates.apps.parts.authentication.fields",
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);
            this.parentRender(() => {
                this.setFormFooterVisiblity(this.data.authFilter.enabled);
                validatorsManager.bindValidators(this.$el);
                _.forEach(this.$el.find(".multi-select-control"), (control) => {
                    FormUtils.initializeMultiSelect(control);
                });
            });
        },

        enableAuthenticationClick (event) {
            const newState = event.currentTarget.checked;
            const collapseState = newState ? "show" : "hide";
            this.$el.find("div[name='authGroup']").collapse(collapseState);

            // Save Enabled or disabled state immediately
            this.data.authFilter.enabled = newState;
            if (!newState) {
                //Save Off state
                this.data.authFilter.enabled = newState;
                this.data.appData.save();
            } else {
                //Save On state, only when form is valid
                const form = this.$el.find("#authForm")[0];
                FormUtils.isFormValid(form).then((valid) => {
                    if (valid) {
                        this.data.authFilter.enabled = newState;
                        this.data.appData.save();
                    }
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
            const form = this.$el.find("#authForm")[0];
            FormUtils.isFormValid(form).then((valid) => {
                if (!valid) {
                    $(form).find("input").trigger("validate");
                    return;
                }
                const formVal = form2js(form, ".", false);
                _.extend(this.data.authFilter, formVal);
                this.data.authFilter.enabled = FormUtils.getBoolValue(formVal.enabled);
                this.data.authFilter.tokenEndpointUseBasicAuth =
                    FormUtils.getBoolValue(formVal.tokenEndpointUseBasicAuth);
                this.data.authFilter.requireHttps = FormUtils.getBoolValue(formVal.requireHttps);
                if (this.data.newFilter) {
                    AppsUtils.addFilterIntoModel(this.data.appData, this.data.authFilter);
                }
                this.data.appData.save();

                EventManager.sendEvent(
                    Constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                    {
                        key: "appSettingsSaveSuccess",
                        filter: i18n.t("templates.apps.parts.authentication.title")
                    }
                );
            });
        },

        getFilter () {
            return _.find(this.data.appData.get("content/filters"),
                { "type": "OAuth2ClientFilter" }
            );
        },

        createFilter () {
            return {
                "type": "OAuth2ClientFilter",
                "scopes": "openid"
            };
        }
    })
));
