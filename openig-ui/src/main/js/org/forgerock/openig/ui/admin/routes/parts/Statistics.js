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
    "selectize",
    "org/forgerock/openig/ui/admin/util/FormUtils",
    "org/forgerock/openig/ui/admin/util/RoutesUtils",
    "org/forgerock/openig/ui/admin/util/ValueHelper",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/AbstractView"
], (
    $,
    _,
    form2js,
    i18n,
    selectize,
    FormUtils,
    RoutesUtils,
    ValueHelper,
    EventManager,
    Constants,
    AbstractView
) => (
    AbstractView.extend({
        element: ".main",
        template: "templates/openig/admin/routes/parts/Statistics.html",
        partials: [
            "templates/openig/admin/common/form/SliderControl.html",
            "templates/openig/admin/common/form/GroupControl.html",
            "templates/openig/admin/common/form/MultiSelectControl.html"
        ],
        events: {
            "click .js-reset-btn": "resetClick",
            "click .js-save-btn": "saveClick",
            "click input[name='enabled']": "enableStatistics",
            "change input[name='percentiles']": "percentilesChange"
        },
        data: {
            formId: "statistics-form"
        },
        initialize (options) {
            this.data = _.extend(this.data, options.parentData);
        },
        spaceDelimiter: " ",
        render () {
            const statistics = this.getStatistics();
            const defaultValues = [
                (0.999).toLocaleString(undefined, { minimumFractionDigits: 3 }),
                (0.9999).toLocaleString(undefined, { minimumFractionDigits: 4 }),
                (0.99999).toLocaleString(undefined, { minimumFractionDigits: 5 })
            ].join(this.spaceDelimiter);

            this.data.controls = [
                {
                    name: "enabled",
                    value: statistics.enabled,
                    controlType: "slider",
                    hint: false
                },
                {
                    name: "statisticsGroup",
                    title: "",
                    controlType: "group",
                    cssClass: this.getStatistics().enabled ? "collapse in" : "collapse",
                    controls: [
                        {
                            name: "percentiles",
                            value: statistics.percentiles,
                            controlType: "multiselect",
                            delimiter: this.spaceDelimiter,
                            options: defaultValues,
                            placeholder: defaultValues
                        }
                    ]
                }
            ];
            FormUtils.extendControlsSettings(this.data.controls, {
                autoTitle: true,
                autoHint: true,
                translatePath: "templates.routes.parts.statistics.fields",
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);
            this.parentRender(() => {
                this.setFormFooterVisiblity(this.getStatistics().enabled);
                _.forEach(this.$el.find(".multi-select-control"), (control) => {
                    const multiselect = FormUtils.initializeMultiSelect(control);
                    multiselect[0].selectize.on("item_add", (value) => { this.onItemAdd(value, multiselect); });
                });
            });
        },

        enableStatistics (event) {
            const newState = event.currentTarget.checked;
            const collapseState = newState ? "show" : "hide";
            this.$el.find("div[name='statisticsGroup']").collapse(collapseState);

            const statistics = this.getStatistics();
            statistics.enabled = newState;
            this.data.routeData.set("statistics", statistics);

            this.data.routeData.save();
            this.setFormFooterVisiblity(newState);
        },

        resetClick (event) {
            event.preventDefault();
            this.render();
        },

        onItemAdd (value, control) {
            const selectize = control[0].selectize;
            const removeItem = () => {
                selectize.removeOption(value);
                selectize.removeItem(value);
            };
            const decimalSeparator = ValueHelper.getDecimalSeparator();
            const float = ValueHelper.toNumber(value);
            const splitNumber = float.toString().split(".");
            // Build number in appropriate localized format.
            // If the number has both, integer and fractional part the fixed consists
            // from '0', separator and 'fractional part';
            // if the number has no fractional part then the fixed consists from '0', 'separator' and 'integer part'
            const fixed = `0${decimalSeparator}${_.last(splitNumber)}`;

            if (!_.isFinite(float)) {
                // in case the value is not a number
                removeItem();
            } else if (!_.isEqual(value, fixed)) {
                // in case the entered value differs from calculated
                removeItem();
                selectize.addOption({ value:fixed, text:fixed });
                selectize.addItem(fixed, true);
            }
        },

        saveClick (event) {
            event.preventDefault();
            const form = this.$el.find(`#${this.data.formId}`)[0];
            this.data.routeData.set("statistics", this.formToStatistics(form));
            this.data.routeData.save();
            this.isDataDirty();

            EventManager.sendEvent(
                Constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                {
                    key: "routeSettingsSaveSuccess",
                    filter: i18n.t("templates.routes.parts.statistics.title")
                }
            );
        },

        percentilesChange () {
            this.isDataDirty();
        },

        // Check entered data against saved
        isDataDirty () {
            const savedVal = _.get(this.data.routeData.get("statistics"), "percentiles");
            const form = this.$el.find(`#${this.data.formId}`)[0];
            const formVal = this.formToStatistics(form).percentiles;
            const submit = this.$el.find(".js-save-btn");

            submit.attr("disabled",
                _.isEqual(
                    _.sortBy(savedVal.split(this.spaceDelimiter)),
                    _.sortBy(formVal.split(this.spaceDelimiter))
                )
            );
        },

        formToStatistics (form) {
            const formVal = form2js(form, ".", false);
            return {
                enabled: FormUtils.getBoolValue(formVal.enabled),
                percentiles: formVal.percentiles
            };
        },

        setFormFooterVisiblity (visible) {
            const footerPanel = this.$el.find(".panel-footer");
            if (visible) {
                footerPanel.show();
            } else {
                footerPanel.hide();
            }
        },

        getStatistics () {
            let statistics = this.data.routeData.get("statistics");
            if (!statistics) {
                statistics = this.defaultStatistics();
            }
            return statistics;
        },

        defaultStatistics () {
            return {
                enabled: false,
                percentiles: ""
            };
        }
    })
));
