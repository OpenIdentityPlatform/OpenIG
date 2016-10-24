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
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/models/SettingsModel"
], (
    $,
    _,
    form2js,
    AbstractView,
    eventManager,
    validatorsManager,
    constants,
    router,
    SettingsModel) => {

    const SettingsView = AbstractView.extend({
        template: "templates/openig/admin/settings/SettingsTemplate.html",
        events: {
            "click #submitSettings": "settingsFormSubmit",
            "onValidate": "onValidate"
        },
        data: {

        },
        appTypeRef: null,
        settings: null,

        render () {
            this.data = {};
            this.data.docHelpUrl = constants.DOC_URL;
            this.settings = new SettingsModel();
            this.data.editState = false;

            this.parentRender(() => {
                validatorsManager.bindValidators(this.$el);
            });
        },

        settingsFormSubmit (event) {
            event.preventDefault();

            if (this.settings && this.settings !== null) {
                // Parse Form values and creates simple content
                const formVal = form2js(this.$el.find("#settings")[0], ".", true);

                // Sets Form values into model
                this.settings.set({
                    "_id": "appSettings_" + Date.now(), // generates new id
                    "admin": formVal.admin,
                    "functional": formVal.functional
                });

                //Validates all inputs
                if (!this.settings.isValid()) {
                    this.$el.find("input[type=text]").trigger("validate");

                    eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, "settingsFailed");

                    const invalidItem = _.first(this.$el.find("input[data-validation-status=error]"));
                    const tabName = invalidItem.closest(".tab-pane").id;
                    $(`.nav-tabs a[href="#${tabName}"]`).tab("show");
                    return;
                }

                //TODO - should be removed from this place
                eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, "settingsSuccess");
            }
        }
    });

    return new SettingsView();
});
