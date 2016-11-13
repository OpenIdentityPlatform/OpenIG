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
    "form2js",
    "selectize",
    "i18next",
    "org/forgerock/openig/ui/admin/routes/AbstractRouteView",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/openig/ui/admin/util/RoutesUtils",
    "org/forgerock/openig/ui/admin/util/FormUtils"
], (
    $,
    _,
    form2js,
    selectize,
    i18n,
    AbstractRouteView,
    validatorsManager,
    RoutesUtils,
    FormUtils
    ) => (
    AbstractRouteView.extend({
        template: "templates/openig/admin/routes/parts/Authentication.html",
        partials: [
            "templates/openig/admin/common/form/EditControl.html",
            "templates/openig/admin/common/form/SliderControl.html",
            "templates/openig/admin/common/form/GroupControl.html",
            "templates/openig/admin/common/form/CheckboxControl.html",
            "templates/openig/admin/common/form/MultiSelectControl.html",
            "templates/openig/admin/routes/components/FormFooter.html"
        ],
        events: {
            "click input[name='enabled']": "enableAuthenticationClick",
            "click .js-reset-btn": "resetClick",
            "click .js-save-btn": "saveClick"
        },
        data: {
            formId: "authentication-form"
        },
        initialize (options) {
            this.data = _.extend(this.data, options.parentData);
            this.filterCondition = { "type": "OAuth2ClientFilter" };
            this.settingTitle = i18n.t("templates.routes.parts.authentication.title");
        },
        render () {
            this.data.authFilter = this.getFilter();
            if (!this.data.authFilter) {
                this.data.authFilter = this.createFilter();
            }

            this.data.controls = [
                {
                    name: "enabled",
                    value:  this.data.authFilter.enabled,
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
                                    value: this.data.authFilter.clientEndpoint,
                                    validator: "required"
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
                                    options: "openid profile email address phone offline_access",
                                    delimiter: " ",
                                    mandatory: "openid"
                                },
                                {
                                    name: "tokenEndpointUseBasicAuth",
                                    value: this.data.authFilter.tokenEndpointUseBasicAuth,
                                    controlType: "slider"
                                },
                                {
                                    name: "requireHttps",
                                    value: this.data.authFilter.requireHttps,
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
                translatePath: "templates.routes.parts.authentication.fields",
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);
            this.parentRender(() => {
                this.setFormFooterVisibility(this.data.authFilter.enabled);
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
                this.data.routeData.setFilter(this.data.authFilter, this.filterCondition);
                this.data.routeData.save()
                    .then(
                        () => {
                            this.showNotification(this.NOTIFICATION_TYPE.Disabled);
                        },
                        () => {
                            this.showNotification(this.NOTIFICATION_TYPE.SaveFailed);
                        }
                    );
            } else {
                //Save On state, only when form is valid
                const form = this.$el.find(`#${this.data.formId}`)[0];
                FormUtils.isFormValid(form)
                    .done(
                    () => {
                        this.data.authFilter.enabled = newState;
                        this.data.routeData.setFilter(this.data.authFilter, this.filterCondition);
                        this.data.routeData.save();
                    });
            }
            this.setFormFooterVisibility(newState);
        },

        saveClick () {
            event.preventDefault();
            const form = this.$el.find(`#${this.data.formId}`)[0];
            FormUtils.isFormValid(form)
                .done(
                () => {
                    const formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
                    _.extend(this.data.authFilter, formVal);
                    if (!this.getFilter()) {
                        RoutesUtils.addFilterIntoModel(this.data.routeData, this.data.authFilter);
                    }
                    this.data.routeData.setFilter(this.data.authFilter, this.filterCondition);
                    this.data.routeData.save()
                        .then(
                            () => {
                                const submit = this.$el.find(".js-save-btn");
                                submit.attr("disabled", true);
                                this.showNotification(this.NOTIFICATION_TYPE.SaveSuccess);
                            },
                            () => {
                                this.showNotification(this.NOTIFICATION_TYPE.SaveFailed);
                            }
                    );
                })
                .fail(
                () => {
                    $(form).find("input").trigger("validate");
                });
        },

        getFilter () {
            return this.data.routeData.getFilter(this.filterCondition);
        },

        createFilter () {
            return {
                type: "OAuth2ClientFilter",
                scopes: "openid",
                clientEndpoint: "/openid"
            };
        }
    })
));
