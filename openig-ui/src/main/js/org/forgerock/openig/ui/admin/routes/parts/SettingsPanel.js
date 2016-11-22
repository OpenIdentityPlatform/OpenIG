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
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/openig/ui/admin/util/RoutesUtils",
    "org/forgerock/openig/ui/admin/util/FormUtils",
    "org/forgerock/openig/ui/admin/services/TransformService"
], (
    $,
    _,
    form2js,
    i18n,
    AbstractView,
    validatorsManager,
    RoutesUtils,
    FormUtils,
    transformService
) => (
    class SettingsPanel extends AbstractView {
        constructor () {
            super();
            this.element = "#settingsPanel";
            this.manualIdChange = false;
            this.translationPath = "templates.routes.parts.settings.fields";
            this.conditionType = { PATH: "path", EXPRESSION: "expression" };
            this.condition = { type: this.conditionType.PATH };
        }

        get template () { return "templates/openig/admin/routes/parts/SettingsPanel.html"; }

        get partials () {
            return [
                "templates/openig/admin/common/form/EditControl.html",
                "templates/openig/admin/common/form/DropdownEditControl.html"
            ];
        }

        get events () {
            return {
                "blur input[name='name']": "validateName",
                "blur input[name='condition']": "onConditionChange",
                "keyup input[name='name']": "generateId",
                "keyup input[name='id']": "validateId",
                "click .condition-link-btn": "switchClick",
                "click ul[name='conditionType']": "onConditionButtonClick",
                "validationSuccessful": "validationSuccessful",
                "validationFailed": "validationFailed"
            };
        }

        setup (options) {
            this.routeData = options.route;
            this.isEdit = this.routeData.has("_id");
        }

        setupForm (data) {
            _.forOwn(data, (value, key) => {
                if (key === "condition") {
                    this.condition = value;
                    this.setSelected(value.type);
                } else {
                    this.$el.find(`input[name='${key}']`).val(value);
                }
            });
        }

        render () {
            this.condition = _.extend(this.condition, this.routeData.get("condition"));
            this.conditionOptions = this.getDropdownItems(this.conditionType);
            this.data.controls = [
                {
                    name: "baseURI",
                    value: this.routeData.get("baseURI"),
                    validator: "required baseURI spaceCheck"
                },
                {
                    name: "condition",
                    controlType: "dropdownedit",
                    dropdownName: "conditionType",
                    selectedOption: `${this.translationPath}.${this.condition.type}`,
                    value: this.condition[this.condition.type],
                    options: this.conditionOptions
                },
                {
                    name: "name",
                    value: this.routeData.get("name"),
                    validator: "required spaceCheck customValidator"
                },
                {
                    name: "id",
                    value: this.routeData.get("id"),
                    validator: "required spaceCheck urlCompatible customValidator",
                    disabled: this.isEdit
                }
            ];
            FormUtils.extendControlsSettings(this.data.controls, {
                autoTitle: true,
                autoHint: true,
                translatePath: this.translationPath,
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);
            this.parentRender(() => {
                validatorsManager.bindValidators(this.$el);
            });
        }

        getDropdownItems (options) {
            return _.sortByOrder(
                _.map(options, (name) => (
                    {
                        name,
                        title: i18n.t(`${this.translationPath}.${name}`)
                    }
                )),
                "title",
                ["asc"]
            );
        }

        setCondition (type, value) {
            if (!type) {
                type = this.condition.type;
            }
            this.condition[type] = value;
        }

        setSelected (option) {
            const conditionField = this.$el.find("[name='condition']");
            const currentOption = this.condition.type;

            if (option !== currentOption) {
                // Store previous value
                this.setCondition(currentOption, conditionField.val());
                this.condition.type = option;
                this.convertCondition(currentOption, option, conditionField.val());
            }

            // Set new value
            conditionField.val(this.condition[option]);

            // Update dropdown button title
            const button = this.$el.find("[name='conditionButton']")[0];
            $(button).html(this.getConditionOption(option).title)
                .append(" <span class='caret'></span>");
        }

        onConditionButtonClick (event) {
            event.preventDefault();
            this.setSelected(event.target.name);
        }

        convertCondition (fromType, toType) {
            if (this.getConditionOption(toType).isChanged &&
                !_.isEmpty(this.condition[toType])) {

                // Never change values edited by user, except when it's empty
                return;
            }
            if (fromType === this.conditionType.PATH &&
                toType === this.conditionType.EXPRESSION) {
                this.condition[toType] = transformService.generateCondition(this.condition[fromType]);
                this.getConditionOption(toType).isChanged = false;
            }
        }

        onConditionChange (event) {
            event.preventDefault();

            // Update condition value on user event
            this.condition[this.condition.type] = event.target.value;
            this.getConditionOption(this.condition.type).isChanged = true;
        }

        getConditionOption (type) {
            return _.find(this.conditionOptions, { name: type });
        }

        save () {
            const form = this.$el[0];
            return FormUtils.isFormValid(form)
                .then(
                    () => {
                        this.routeData.set(this.getFormData(form));
                        return this.routeData.save();
                    },
                    () => {
                        $(form).find("input").trigger("validate");
                    }
                );
        }

        getFormData (form) {
            const formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);

            // update 'condition' value to keep all values
            formVal.condition = this.condition;
            return formVal;
        }

        validate () {
            const form = this.$el.closest("form");
            return FormUtils.isFormValid(form)
                .then(
                    () => {
                        $(form).find("input").trigger("validate");
                    }
                );
        }

        generateId (evt) {
            // Avoid re-generate on tab, after manual change or at edit page
            if (evt.keyCode === 9 || this.manualIdChange || this.isEdit) {
                return;
            }
            this.$el.find("[name='id']").val(RoutesUtils.generateRouteId(evt.target.value));
        }

        validateId (evt) {
            const deferred = $.Deferred();
            const target = this.$el.find("[name='id']")[0];
            if (this.routeData.get("id") !== target.value) {
                RoutesUtils.isRouteIdUniq(target.value)
                    .then((isValid) => {
                        $(target).data("custom-valid-msg", (isValid ? "" : "templates.routes.duplicateIdError"));
                        $(target).trigger("validate");
                        deferred.resolve(
                            isValid
                        );
                    });
            }
            if (evt && evt.keyCode !== 9) {
                this.manualIdChange = true;
            }
            return deferred;
        }

        validateName () {
            const target = this.$el.find("[name='name']")[0];
            if (this.routeData.get("name") !== target.value) {
                RoutesUtils.checkName(target.value)
                    .then(
                    (checkResult) => {
                        $(target).data("custom-valid-msg", checkResult || "");
                        $(target).trigger("validate");
                    });
            }
        }
    })
);
