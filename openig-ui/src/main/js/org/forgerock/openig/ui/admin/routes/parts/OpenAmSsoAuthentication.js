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
    class OpenAmSsoAuthentication extends AbstractAuthenticationFilterView {
        get partials () {
            return [
                "templates/openig/admin/common/form/EditControl.html"
            ];
        }

        initialize (options) {
            this.data = _.clone(options.data);
            this.data.formId = "sso-authentication-form";
            this.filterCondition = { "type": "SingleSignOnFilter" };
            this.translatePath = "templates.routes.parts.openAmSsoAuthentication";
            this.settingTitle = i18n.t(`${this.translatePath}.title`);
            this.initializeFilter();
            this.prepareControls();
        }

        prepareControls () {
            this.data.controls = [
                {
                    name: "openamUrl",
                    value: this.data.filter.openamUrl,
                    validator: "required"
                },
                {
                    name: "realm",
                    value: this.data.filter.realm
                },
                {
                    name: "cookieName",
                    value: this.data.filter.cookieName
                }
            ];
        }

        createFilter () {
            return {
                type: "SingleSignOnFilter",
                openamUrl: "",
                realm: "/",
                cookieName: "iPlanetDirectoryPro"
            };
        }
    }
));
