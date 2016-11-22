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
    "org/forgerock/openig/ui/admin/routes/AbstractRouteView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/routes/parts/SettingsPanel",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/models/RouteModel",
    "org/forgerock/openig/ui/admin/models/RoutesCollection",
    "org/forgerock/commons/ui/common/main/ValidatorsManager"
], (
    $,
    _,
    AbstractRouteView,
    EventManager,
    Constants,
    SettingsPanel,
    router,
    RouteModel,
    RoutesCollection,
    validatorsManager
) => {
    class AddRouteView extends AbstractRouteView {
        constructor () {
            super();
            this.translationPath = "templates.routes.parts.settings.fields";
            this.settingsPanel = new SettingsPanel();
            this.data.formId = "add-route";
        }

        get element () {
            return "#content";
        }

        get template () {
            return "templates/openig/admin/routes/AddRouteTemplate.html";
        }

        get events () {
            return {
                "click .js-create-btn": "createClick",
                "click .js-cancel-btn": "cancelClick",
                "click .js-advanced-btn": "openAdvanced",
                "click .js-basic-btn": "closeAdvanced",
                "click [name='conditionType']": "conditionChanged"
            };
        }

        render () {
            this.isChanged = false;
            this.setupRoute()
                .then((routeData) => {
                    this.routeData = routeData;
                    this.data.advancedOnly = this.isDuplicate();
                    this.parentRender(() => {
                        validatorsManager.bindValidators(this.$el);
                        this.settingsPanel.setup({ route: routeData });
                        this.settingsPanel.render();
                    });
                });
        }

        setupRoute () {
            this.data.routeId = router.getCurrentHash().match(router.currentRoute.url)[1];
            if (this.data.routeId) {
                return RoutesCollection.byRouteId(this.data.routeId)
                        .then((original) => {
                            const duplicate = original.clone();
                            duplicate.unset("_id");
                            return duplicate;
                        });
            } else {
                return RouteModel.newRouteModel();
            }
        }

        fillAdvancedForm () {
            const input = this.$el.find("input[name='applicationUrl']");
            this.settingsPanel.setupForm(
                this.getParsedData(input.val())
            );
        }

        fillBasicForm () {
            const baseURI = this.$el.find("input[name='baseURI']").val();
            const condition = this.$el.find("input[name='condition']").val();
            this.$el.find("input[name='applicationUrl']").val(baseURI + condition);
        }

        openAdvanced (event) {
            event.preventDefault();
            this.fillAdvancedForm();
            this.settingsPanel.validate();
            this.swapOptions();
        }

        closeAdvanced (event) {
            event.preventDefault();
            if ($(event.target).hasClass("disabled")) {
                return;
            }
            this.fillBasicForm();
            this.swapOptions();
            this.toggleCreateButton(true);
        }

        toggleCreateButton (enable) {
            this.$el.find(".js-create-btn").attr("disabled", !enable);
        }

        swapOptions () {
            this.$el.find(".basic-settings").toggleClass("hidden");
            this.$el.find(".advanced-settings").toggleClass("hidden");
        }

        conditionChanged (event) {
            const button = this.$el.find(".js-basic-btn");
            const tooltip = button.closest("div");
            const disable = event.target.name === "expression";
            button.toggleClass("disabled", disable);
            if (disable) {
                tooltip.tooltip();
            } else {
                tooltip.tooltip("destroy");
            }
        }

        getParsedData (applUrl) {
            const match = /(http[s]?:\/\/[^\/\s]+)(\/\w*)?/i.exec(applUrl);
            if (match) {
                const path = match[2];
                const name = path ? path.slice(1) : "";
                return {
                    baseURI: match[1],
                    condition: this.createPathCondition(path),
                    name,
                    id: name
                };
            } else {
                return this.getDefaults(applUrl);
            }
        }

        createPathCondition (path) {
            return {
                type: "path",
                path
            };
        }

        getDefaults (value) {
            return {
                baseURI: value,
                condition: this.createPathCondition(),
                name: "",
                id: ""
            };
        }

        createClick (event) {
            event.preventDefault();
            const isAdvancedOpen = this.isAdvancedOpen();
            if (!isAdvancedOpen) {
                this.fillAdvancedForm();
            }
            this.settingsPanel.validate()
                .then(
                    () => {
                        this.createRoute();
                    },
                    () => {
                        this.$el.find("input").trigger("validate");
                        if (!isAdvancedOpen) {
                            this.swapOptions();
                        }
                    }
                );
        }

        createRoute () {
            this.settingsPanel.save()
                .then(
                    (newRoute) => {
                        RoutesCollection.add([
                            newRoute
                        ]);
                        EventManager.sendEvent(Constants.EVENT_CHANGE_VIEW, {
                            route: router.configuration.routes.routeOverview,
                            args: [newRoute.id]
                        });
                    },
                    () => {
                        EventManager.sendEvent(
                            Constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                            {
                                key: "routeCreationFailed"
                            }
                        );
                    }
                );
        }

        cancelClick () {
            EventManager.sendEvent(
                Constants.EVENT_CHANGE_VIEW, {
                    route: router.configuration.routes.listRoutesView
                }
            );
        }

        isAdvancedOpen () {
            return !this.$el.find(".advanced-settings").hasClass("hidden");
        }

        isDuplicate () {
            return router.configuration.routes.duplicateRouteView.url.test(router.getURIFragment());
        }
    }

    return new AddRouteView();
});
