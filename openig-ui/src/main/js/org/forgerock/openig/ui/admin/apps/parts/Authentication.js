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
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/openig/ui/admin/util/FormUtils",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants"
], (
    $,
    _,
    form2js,
    selectize,
    AbstractView,
    validatorsManager,
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
            this.data.authFilter = _.find(this.data.appData.get("content/filters"), { "type": "OAuth2ClientFilter" });

            this.data.controls = [
                {
                    name: "enabled",
                    value:  (_.get(this.data.authFilter, "enabled", false) === true) ? "checked" : "",
                    controlType: "slider"
                },
                {
                    name: "authGroup",
                    title: "",
                    controlType: "group",
                    cssClass: (_.get(this.data.authFilter, "enabled", false) === true) ? "collapse in" : "collapse",
                    controls: [
                        {
                            title: "Client Filter",
                            name: "clientFilterGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "clientEndpoint",
                                    value: _.get(this.data.authFilter, "clientEndpoint")
                                }
                            ]
                        },
                        {
                            title: "Client Registration",
                            name: "clientRegistrationGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "clientId",
                                    value: _.get(this.data.authFilter, "clientId"),
                                    validator: "required"
                                },
                                {
                                    name: "clientSecret",
                                    value: _.get(this.data.authFilter, "clientSecret"),
                                    validator: "required"
                                },
                                {
                                    name: "scopes",
                                    value: _.get(this.data.authFilter, "scopes"),
                                    controlType: "multiselect",
                                    options: ["openid", "profile", "email", "address", "phone", "offline_access"],
                                    delimiter: " ",
                                    validator: "required"
                                },
                                {
                                    name: "tokenEndpointUseBasicAuth",
                                    value: _.get(this.data.authFilter, "tokenEndpointUseBasicAuth"),
                                    controlType: "checkbox"
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
                                    value: _.get(this.data.authFilter, "issuerWellKnownEndpoint"),
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
                this.setFormFooterVisiblity((_.get(this.data.authFilter, "enabled", false) === true));
                validatorsManager.bindValidators(this.$el);
                _.forEach(this.$el.find(".multi-select-control"), (control) => {
                    FormUtils.initializeMultiSelect(control);
                });
            });
        },

        enableAuthenticationClick (event) {
            if (event.currentTarget.checked) {
                this.$el.find("div[name='authGroup']").collapse("show");
            } else {
                this.$el.find("div[name='authGroup']").collapse("hide");
            }

            // Save Enabled or disabled state immediately
            _.set(this.data.authFilter, "enabled", event.currentTarget.checked);
            this.data.appData.save();

            this.setFormFooterVisiblity(event.currentTarget.checked);
        },

        setFormFooterVisiblity (visible) {
            if (visible) {
                this.$el.find(".panel-footer").show();
            } else {
                this.$el.find(".panel-footer").hide();
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
                this.data.appData.save();

                EventManager.sendEvent(
                    Constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                    {
                        key: "appSettingsSaveSuccess",
                        filter: $.t("templates.apps.parts.authentication.title")
                    }
                );
            });
        }
    })
));
