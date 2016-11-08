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
    "org/forgerock/openig/ui/admin/routes/AbstractRouteView",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/openig/ui/admin/util/RoutesUtils",
    "org/forgerock/openig/ui/admin/util/FormUtils"
], (
    $,
    _,
    form2js,
    selectize,
    i18n,
    AbstractRouteView,
    validatorsManager,
    RoutesUtils,
    FormUtils
) => (
    class AbstractAuthenticationFilterView extends AbstractRouteView {
        get template () {
            return "templates/openig/admin/routes/parts/AuthenticationDialog.html";
        }

        initializeFilter () {
            this.data.filter = this.getFilter();
            if (!this.data.filter && this.createFilter) {
                this.data.filter = this.createFilter();
            }
        }

        isFilterEnabled () {
            const filter = this.data.routeData.getFilter(this.filterCondition);
            return filter && filter.enabled;
        }

        render () {
            if (this.prepareControls) {
                this.prepareControls();
            }
            FormUtils.extendControlsSettings(this.data.controls, {
                autoTitle: true,
                autoHint: true,
                translatePath: `${this.translatePath}.fields`,
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);

            this.parentRender(() => {
                validatorsManager.bindValidators(this.$el);
                _.forEach(this.$el.find(".multi-select-control"), (control) => {
                    FormUtils.initializeMultiSelect(control);
                });
            });
        }

        toggleFilter (enabled) {
            this.data.filter.enabled = enabled;
            this.data.routeData.setFilter(this.data.filter, this.filterCondition);
        }

        save () {
            const form = this.$el.find(`#${this.data.formId}`)[0];
            return FormUtils.isFormValid(form)
                .done(
                    () => {
                        const formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
                        _.extend(this.data.filter, formVal);
                        if (!this.getFilter()) {
                            RoutesUtils.addFilterIntoModel(this.data.routeData, this.data.filter);
                        }
                        this.data.routeData.setFilter(this.data.filter, this.filterCondition);
                        return this.data.routeData.save()
                            .then(
                                () => {
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
        }

        getFilter () {
            return this.data.routeData.getFilter(this.filterCondition);
        }
    }
));
