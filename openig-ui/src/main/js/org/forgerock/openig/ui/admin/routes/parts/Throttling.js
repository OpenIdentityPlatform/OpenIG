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
    "i18next",
    "org/forgerock/openig/ui/admin/routes/AbstractRouteView",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/openig/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/util/RoutesUtils",
    "org/forgerock/openig/ui/admin/util/FormUtils"
], (
    $,
    _,
    form2js,
    i18n,
    AbstractRouteView,
    validatorsManager,
    Constants,
    RoutesUtils,
    FormUtils
) => (AbstractRouteView.extend({
    template: "templates/openig/admin/routes/parts/Throttling.html",
    partials: [
        "templates/openig/admin/common/form/SliderControl.html",
        "templates/openig/admin/common/form/GroupControl.html",
        "templates/openig/admin/routes/components/ThrottlingControl.html",
        "templates/openig/admin/routes/components/FormFooter.html"
    ],
    events: {
        "click .js-reset-btn": "resetClick",
        "click .js-save-btn": "saveClick",
        "click input[name='enabled']": "enableThrottlingClick"
    },
    options: [
        Constants.timeSlot.SECOND,
        Constants.timeSlot.MINUTE,
        Constants.timeSlot.HOUR
    ],
    data: {
        formId: "throttling-form"
    },

    initialize (options) {
        this.data = _.extend(this.data, options.parentData);
        this.filterCondition = { "type": "ThrottlingFilter" };
        this.settingTitle = i18n.t("templates.routes.parts.throttling.title");
    },

    render () {
        this.data.throttFilter = this.getFilter();
        if (!this.data.throttFilter) {
            this.data.throttFilter = this.createFilter();
        }

        this.data.controls = [
            {
                name: "enabled",
                title: i18n.t("templates.routes.parts.throttling.btnEnableTitle"),
                value: this.data.throttFilter.enabled ? "checked" : "",
                controlType: "slider"
            },
            {
                name: "throttGroup",
                title: "",
                controlType: "group",
                cssClass: this.data.throttFilter.enabled ? "collapse in" : "collapse",
                controls: [
                    {
                        controlType: "throttling",
                        requests: this.data.throttFilter.numberOfRequests,
                        duration: this.data.throttFilter.durationValue,
                        validator: "required greaterThanOrEqualMin",
                        template: "templates/openig/admin/routes/components/ThrottlingControl"
                    }
                ]
            }
        ];

        FormUtils.fillPartialsByControlType(this.data.controls);
        this.parentRender(() => {
            this.createTimeRangeSelect();
            this.setFormFooterVisibility(this.data.throttFilter.enabled);
            validatorsManager.bindValidators(this.$el);
        });
    },

    createTimeRangeSelect () {
        const selectList = this.$el.find("select[name='durationRange']")[0];
        _.each(this.options, (opt) => {
            const option = document.createElement("option");
            option.value = opt;
            option.text = i18n.t(`common.timeSlot.${opt}`);
            selectList.appendChild(option);
            selectList.value = this.data.throttFilter.durationRange;
        });
    },

    saveClick (event) {
        event.preventDefault();
        const form = this.$el.find(`#${this.data.formId}`)[0];
        FormUtils.isFormValid(form)
            .done(
            () => {
                const formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
                _.extend(this.data.throttFilter, formVal);
                if (!this.getFilter()) {
                    RoutesUtils.addFilterIntoModel(this.data.routeData, this.data.throttFilter);
                }
                this.data.routeData.setFilter(this.data.throttFilter, this.filterCondition);
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

    enableThrottlingClick (event) {
        const newState = event.currentTarget.checked;
        const collapseState = newState ? "show" : "hide";
        this.$el.find("div[name='throttGroup']").collapse(collapseState);

        // Save Enabled or disabled state immediately
        if (!newState) {
            // Save Off state
            this.data.throttFilter.enabled = newState;
            this.data.routeData.setFilter(this.data.throttFilter, this.filterCondition);
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
            // Save On state, only when form is valid
            const form = this.$el.find(`#${this.data.formId}`)[0];
            FormUtils.isFormValid(form)
                .done(
                () => {
                    this.data.throttFilter.enabled = newState;
                    this.data.routeData.setFilter(this.data.throttFilter, this.filterCondition);
                    this.data.routeData.save();
                });
        }
        this.setFormFooterVisibility(newState);
    },

    getFilter () {
        return this.data.routeData.getFilter(this.filterCondition);
    },

    createFilter () {
        return {
            type: "ThrottlingFilter",
            enabled: false,
            numberOfRequests: 100,
            durationValue: 1,
            durationRange: Constants.timeSlot.SECOND
        };
    }
})));
