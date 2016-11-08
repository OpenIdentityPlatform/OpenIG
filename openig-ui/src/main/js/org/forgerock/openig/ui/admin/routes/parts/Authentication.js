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
    "org/forgerock/commons/ui/common/components/BootstrapDialog",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/openig/ui/admin/routes/parts/OpenIDAuthentication",
    "org/forgerock/openig/ui/admin/routes/parts/OpenAmSsoAuthentication"
], (
    $,
    _,
    form2js,
    selectize,
    i18n,
    AbstractRouteView,
    BootstrapDialog,
    UIUtils,
    OpenIDAuthentication,
    OpenAmSsoAuthentication
) => (
    class Authentication extends AbstractRouteView {
        get template () {
            return "templates/openig/admin/routes/parts/Authentication.html";
        }

        get partials () {
            return [
                "templates/openig/admin/common/form/SliderControl.html",
                "templates/openig/admin/routes/components/AuthRadioItem.html"
            ];
        }

        get events () {
            return {
                "click #enableAuthentication": "enableAuthenticationClick",
                "click .js-settings-btn": "settingsClick",
                "click input[type='radio']": "radioClick"
            };
        }

        initialize (options) {
            this.data = options.parentData;
            this.settingTitle = i18n.t("templates.routes.parts.authentication.title");
            this.filters = {
                openid: new OpenIDAuthentication({ data: this.data }),
                sso: new OpenAmSsoAuthentication({ data: this.data })
            };
            this.prevFilterName = this.getActiveFilterName();
        }

        render () {
            this.data.items = [
                {
                    icon: "fa-openid",
                    title: i18n.t("templates.routes.parts.authentication.fields.openID"),
                    hint: i18n.t("templates.routes.parts.authentication.fields.openIDHint"),
                    name: "openid",
                    enabled: this.filters.openid.isFilterEnabled()
                },
                {
                    img: "img/forgerock-mark-white.png",
                    title: i18n.t("templates.routes.parts.authentication.fields.sso"),
                    hint: i18n.t("templates.routes.parts.authentication.fields.ssoHint"),
                    name: "sso",
                    enabled: this.filters.sso.isFilterEnabled()
                }
            ];
            this.data.enabled = _.some(this.data.items, { enabled: true });
            if (this.data.enabled) {
                this.setFilterOption(_.find(this.data.items, { enabled: true }).name);
            }
            this.parentRender();
        }

        getActiveFilterName () {
            let filterName;
            _.find(this.filters, (filter, name) => {
                if (filter.isFilterEnabled()) {
                    filterName = name;
                    return true;
                }
            });
            return filterName;
        }

        refreshOptions () {
            this.setFilterOption(this.getActiveFilterName());
        }

        enableAuthenticationClick (event) {
            const newState = event.currentTarget.checked;
            this.$el.find("#authOptions").toggle(newState);
            if (!newState) {
                this.setFilterOption();
            }
        }

        radioClick (event) {
            this.setFilterOption(event.currentTarget.value);
        }

        setFilterOption (checkedName) {
            let dialogNotification = false;
            this.prevFilterName = this.getActiveFilterName();
            _.each(this.$el.find("input[type='radio']"), (radio) => {
                const item = $(radio).closest(".js-radio-item");
                const edit = item.find(".js-edit-panel");
                const checked = checkedName === radio.value;
                const filterItem = this.filters[radio.value];
                const filter = filterItem.getFilter();
                item.toggleClass("disabled", !checked);
                edit.toggleClass("hidden", !checked);
                if (filter) {
                    filterItem.toggleFilter(checked);
                } else if (checked) {
                    this.showSettingsDialog(filterItem);
                    dialogNotification = true;
                }
                radio.checked = checked;
            });
            if (this.prevFilterName !== checkedName) {
                this.data.routeData.save()
                    .then(
                        () => {
                            if (!dialogNotification) {
                                this.showNotification(
                                    checkedName ? this.NOTIFICATION_TYPE.SaveSuccess : this.NOTIFICATION_TYPE.Disabled
                                );
                            }
                        },
                        () => {
                            this.showNotification(this.NOTIFICATION_TYPE.SaveFailed);
                        }
                    );
            }
        }

        settingsClick (event) {
            this.showSettingsDialog(this.filters[event.currentTarget.name]);
        }

        showSettingsDialog (settings) {
            const message = $("<div></div>");
            settings.element = message;
            settings.render();
            this.delegateEvents();

            BootstrapDialog.show({
                title: i18n.t(`${settings.translatePath}.dialogTitle`),
                message,
                cssClass: "filter-dialog",
                animate: false,
                closable: false,
                size: BootstrapDialog.SIZE_WIDE,
                buttons: [
                    {
                        label: i18n.t("common.form.cancel"),
                        action: (dialog) => {
                            if (!settings.isFilterEnabled()) {
                                this.setFilterOption(this.prevFilterName);
                            }
                            dialog.close();
                        }
                    },
                    {
                        label: i18n.t("common.form.save"),
                        cssClass: "btn-primary",
                        action: (dialog) => {
                            settings.toggleFilter(true);
                            settings.save().then(
                                () => {
                                    this.refreshOptions();
                                    dialog.close();
                                }
                            );
                        }
                    }
                ]
            });
        }
    }
));
