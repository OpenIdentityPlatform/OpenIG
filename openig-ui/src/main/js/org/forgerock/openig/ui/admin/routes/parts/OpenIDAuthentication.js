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
    "lodash",
    "i18next",
    "org/forgerock/openig/ui/admin/routes/AbstractAuthenticationFilterView"
], (
    _,
    i18n,
    AbstractAuthenticationFilterView
) => (
    class OpenIDAuthentication extends AbstractAuthenticationFilterView {
        get partials () {
            return [
                "templates/openig/admin/common/form/EditControl.html",
                "templates/openig/admin/common/form/SliderControl.html",
                "templates/openig/admin/common/form/GroupControl.html",
                "templates/openig/admin/common/form/CheckboxControl.html",
                "templates/openig/admin/common/form/MultiSelectControl.html"
            ];
        }

        initialize (options) {
            this.data = _.clone(options.data);
            this.data.formId = "openid-authentication-form";
            this.filterCondition = { "type": "OAuth2ClientFilter" };
            this.translatePath = "templates.routes.parts.openIDAuthentication";
            this.settingTitle = i18n.t(`${this.translatePath}.title`);
            this.initializeFilter();
            this.prepareControls();
        }

        prepareControls () {
            this.data.controls = [
                {
                    name: "clientFilterGroup",
                    controlType: "group",
                    controls: [
                        {
                            name: "clientEndpoint",
                            value: this.data.filter.clientEndpoint
                        }
                    ]
                },
                {
                    name: "clientRegistrationGroup",
                    controlType: "group",
                    controls: [
                        {
                            name: "clientId",
                            value: this.data.filter.clientId,
                            validator: "required"
                        },
                        {
                            name: "clientSecret",
                            value: this.data.filter.clientSecret,
                            validator: "required"
                        },
                        {
                            name: "scopes",
                            value: this.data.filter.scopes,
                            controlType: "multiselect",
                            options: "openid profile email address phone offline_access",
                            delimiter: " ",
                            mandatory: "openid"
                        },
                        {
                            name: "tokenEndpointUseBasicAuth",
                            value: this.data.filter.tokenEndpointUseBasicAuth,
                            controlType: "slider"
                        },
                        {
                            name: "requireHttps",
                            value: this.data.filter.requireHttps,
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
                            value: this.data.filter.issuerWellKnownEndpoint,
                            validator: "required uri"
                        }
                    ]
                }
            ];
        }

        createFilter () {
            return {
                type: "OAuth2ClientFilter",
                scopes: "openid"
            };
        }
    }
));
