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
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/openig/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/util/FormUtils",
    "org/forgerock/commons/ui/common/main/EventManager"
], (
    $,
    _,
    form2js,
    AbstractView,
    validatorsManager,
    Constants,
    FormUtils,
    EventManager
) => (AbstractView.extend({
    element: ".main",
    template: "templates/openig/admin/apps/parts/Throttling.html",
    events: {
        "click #resetThrottling": "throttlingReset",
        "click #saveThrottling": "throttlingSave",
        "click input[name='enabled']": "enableThrottlingClick",
        "onValidate": "onValidate"
    },
    options: [
        Constants.timeSlot.SECOND,
        Constants.timeSlot.MINUTE,
        Constants.timeSlot.HOUR
    ],
    data: {
    },

    initialize (opts) {
        this.data = opts.parentData;
    },

    render () {
        this.data.throttFilter = _.find(this.data.appData.get("content/filters"), {
            "type": "ThrottlingFilter"
        });

        this.data.sliderValue = (_.get(this.data.throttFilter, "enabled", false) === true)
                ? "checked" : "";
        this.data.cssClass = (_.get(this.data.throttFilter, "enabled", false) === true)
                ? "collapse in" : "collapse";
        this.data.requests = _.get(this.data.throttFilter, "numberOfRequests", 1);
        this.data.duration = _.get(this.data.throttFilter, "durationValue", 1);

        this.parentRender(() => {
            this.createTimeRangeSelect();
            this.setFormFooterVisiblity((_.get(this.data.throttFilter, "enabled", false) === true));
            validatorsManager.bindValidators(this.$el);
        });
    },

    createTimeRangeSelect () {
        const selectList = this.$el.find("select[name='durationRange']")[0];
        _.each(this.options, (opt) => {
            const option = document.createElement("option");
            option.value = opt;
            option.text = $.t(`common.timeSlot.${opt}`);
            selectList.appendChild(option);
            selectList.value = _.get(this.data.throttFilter, "durationRange", Constants.timeSlot.SECOND);
        });
    },

    throttlingReset () {
        event.preventDefault();
        this.render();
    },

    throttlingSave () {
        event.preventDefault();
        const form = this.$el.find("#throttForm")[0];
        FormUtils.isFormValid(form).then((valid) => {
            if (!valid) {
                $(form).find("input").trigger("validate");
                return;
            }
            const formVal = form2js(form, ".", false);
            _.extend(this.data.throttFilter, formVal);
            this.data.throttFilter.enabled = FormUtils.getBoolValue(formVal.enabled);
            this.data.appData.save();

            EventManager.sendEvent(
                Constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                {
                    key: "appSettingsSaveSuccess",
                    filter: $.t("templates.apps.parts.throttling.title")
                }
            );
        });
    },

    enableThrottlingClick (event) {
        if (event.target.checked) {
            this.$el.find("div[name='throttGroup']").collapse("show");
        } else {
            this.$el.find("div[name='throttGroup']").collapse("hide");
        }

        // Save Enabled or disabled state immediately
        _.set(this.data.throttFilter, "enabled", event.currentTarget.checked);
        this.data.appData.save();
        this.setFormFooterVisiblity(event.currentTarget.checked);
    },

    setFormFooterVisiblity (visible) {
        if (visible) {
            this.$el.find(".panel-footer").show();
        } else {
            this.$el.find(".panel-footer").hide();
        }
    }
})));
