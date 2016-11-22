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
    "i18next",
    "org/forgerock/openig/ui/admin/routes/AbstractRouteView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/models/RoutesCollection",
    "org/forgerock/openig/ui/admin/routes/parts/SettingsPanel"
], (
    $,
    _,
    i18n,
    AbstractRouteView,
    eventManager,
    constants,
    Router,
    RoutesCollection,
    SettingsPanel
) => (
    class Settings extends AbstractRouteView {
        constructor () {
            super();
            this.element = ".main";
            this.data = _.extend(this.data, { formId: "settings" });
            this.translationPath = "templates.routes.parts.settings.fields";
            this.settingsPanel = new SettingsPanel();
        }

        get template () { return "templates/openig/admin/routes/parts/Settings.html"; }

        get partials () {
            return [
                "templates/openig/admin/routes/components/FormFooter.html"
            ];
        }

        get events () {
            return {
                "click .js-save-btn": "saveClick",
                "click .js-reset-btn": "resetClick"
            };
        }

        render () {
            this.data.routePath = Router.getCurrentHash().match(Router.currentRoute.url)[1];
            RoutesCollection.byRouteId(this.data.routePath)
                .then((routeData) => {
                    this.routeData = routeData;
                    this.parentRender(() => {
                        this.settingsPanel.setup({ route: routeData });
                        this.settingsPanel.render();
                    });
                });
        }

        saveClick (event) {
            event.preventDefault();
            this.settingsPanel.save()
                .then(
                () => {
                    eventManager.sendEvent(
                        constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                        {
                            key: "routeSettingsSaveSuccess",
                            filter: this.routeData.get("name")
                        }
                    );
                },
                () => {
                    eventManager.sendEvent(
                        constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                        {
                            key: "routeSettingsSaveFailed",
                            filter: this.routeData.get("name")
                        }
                    );
                }
                );
        }

        resetClick (event) {
            event.preventDefault();
            eventManager.sendEvent(
                constants.EVENT_CHANGE_VIEW,
                {
                    route: Router.configuration.routes.routeSettings, args: [this.routeData.get("id")]
                }
            );
        }
    })
);
