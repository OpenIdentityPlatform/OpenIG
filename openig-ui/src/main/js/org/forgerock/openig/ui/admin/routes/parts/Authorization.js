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
    selectize,
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
            "templates/openig/admin/common/form/GroupControl.html",
            "templates/openig/admin/routes/components/FormFooter.html",
            "templates/openig/admin/common/form/MultiSelectControl.html"
        ],
        events: {
            "click input[name='enabled']": "enableAuthorizationClick",
            "click .js-reset-btn": "cancelClick",
            "click .js-save-btn": "saveClick"
        },
        data: {
            formId: "authorization-form"
        },
        initialize (options) {
            this.data = _.extend(this.data, options.parentData);
            this.filterCondition = { "type": "PolicyEnforcementFilter" };
        },
        render () {
            this.data.authZFilter = this.getFilter();
            if (!this.data.authZFilter) {
                this.data.authZFilter = this.createFilter();
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
                                    name: "pepRealm",
                                    value: this.data.authZFilter.pepRealm
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
                                    name: "application",
                                    value: this.data.authZFilter.application
                                },
                                {
                                    name: "ssoTokenSubject",
                                    value: this.data.authZFilter.ssoTokenSubject
                                },
                                {
                                    name: "jwtSubject",
                                    value: this.data.authZFilter.jwtSubject
                                }
                            ]
                        },
                        {
                            name: "contextualAuthGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "headers",
                                    value: this.data.authZFilter.headers,
                                    controlType: "multiselect",
                                    options: "User-Agent Host From Referer Via X-Forwarded-For",
                                    delimiter: " "
                                },
                                {
                                    name: "address",
                                    value: this.data.authZFilter.address,
                                    controlType: "slider"
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
                _.forEach(this.$el.find(".multi-select-control"), (control) => {
                    FormUtils.initializeMultiSelect(control);
                });
            });
        },

        enableAuthorizationClick (event) {
            const newState = event.currentTarget.checked;
            const collapseState = newState ? "show" : "hide";
            this.$el.find("div[name='authZGroup']").collapse(collapseState);

            if (!newState) {
                //Save Off state
                this.data.authZFilter.enabled = newState;
                this.data.routeData.setFilter(this.data.authZFilter, this.filterCondition);
                this.data.routeData.save();
            } else {
                //Save On state, only when form is valid
                const form = this.$el.find(`#${this.data.formId}`)[0];
                FormUtils.isFormValid(form)
                    .done(
                    () => {
                        this.data.authZFilter.enabled = newState;
                        this.data.routeData.setFilter(this.data.authZFilter, this.filterCondition);
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
            const form = this.$el.find(`#${this.data.formId}`)[0];
            FormUtils.isFormValid(form)
                .done(
                () => {
                    const formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
                    _.extend(this.data.authZFilter, formVal);
                    if (!this.getFilter()) {
                        RoutesUtils.addFilterIntoModel(this.data.routeData, this.data.authZFilter);
                    }
                    this.data.routeData.setFilter(this.data.authZFilter, this.filterCondition);
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
            return this.data.routeData.getFilter(this.filterCondition);
        },

        createFilter () {
            return {
                type: "PolicyEnforcementFilter",
                headers: "User-Agent"
            };
        }
    })
));
