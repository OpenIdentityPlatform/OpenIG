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
    "backbone",
    "i18next",
    "bootstrap",
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/common/util/ExternalLinks",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/util/AppsUtils",
    "backgrid",
    "org/forgerock/commons/ui/common/util/BackgridUtils",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/openig/ui/admin/models/AppsCollection",
    "org/forgerock/openig/ui/admin/models/RoutesCollection",
    "org/forgerock/openig/ui/admin/views/common/NoItemBox",
    "org/forgerock/commons/ui/common/util/CookieHelper",
    "org/forgerock/openig/ui/admin/util/DataFilter"
], (
    $,
    _,
    Backbone,
    i18n,
    bootstrap,
    AbstractView,
    eventManager,
    constants,
    externalLinks,
    router,
    appsUtils,
    Backgrid,
    BackgridUtils,
    UIUtils,
    AppsCollection,
    RoutesCollection,
    NoItemBox,
    CookieHelper,
    DataFilter
) => {
    const AppsListView = AbstractView.extend({
        template: "templates/openig/admin/apps/AppsListViewTemplate.html",
        partials: [
            "templates/openig/admin/apps/components/AppCard.html",
            "templates/openig/admin/apps/components/AppPopupMenu.html"
        ],
        events: {
            "click .app-export": "exportAppConfig",
            "click .app-duplicate": "duplicateAppConfig",
            "click .app-deploy": "deployApp",
            "click .app-undeploy": "undeployApp",
            "click .app-delete": "deleteApps",
            "click .toggle-view-btn": "toggleButtonChange",
            "keyup .filter-input": "filterApps",
            "paste .filter-input": "filterApps"
        },
        viewStyleTypes: { card: "card", grid: "grid" },
        listStyleCookieName: "openig-ui-apps-list-style",
        model: {

        },
        render (args, callback) {
            const viewThis = this;

            // Get and refresh view style setting
            this.data.viewStyle = CookieHelper.getCookie(this.listStyleCookieName) || this.viewStyleTypes.card;
            this.saveListViewStyle(this.data.viewStyle);

            UIUtils.preloadPartial("templates/openig/admin/apps/components/AppPopupMenu.html");
            const TemplateCell = Backgrid.Cell.extend({
                render () {
                    UIUtils.fillTemplateWithData(this.template, viewThis.getRenderData(this.model), (content) => {
                        const className = this.column.get("className");
                        if (className) {
                            this.$el.attr("class", className);
                        }
                        this.$el.html(content);
                        this.delegateEvents();
                    });
                    return this;
                }
            });

            // Render data attributes for click, context menu and filter
            const RenderRow = Backgrid.Row.extend({
                render () {
                    RenderRow.__super__.render.apply(this, arguments);
                    if (this.model) {
                        this.$el.attr("data-id", this.model.get("_id"));
                        this.$el.attr("data-name", this.model.get("content/name"));
                        this.$el.addClass("app-item");
                    }
                    return this;
                }
            });

            const tableColumns = [
                {
                    name: "name",
                    label: i18n.t("templates.apps.tableColumns.name"),
                    sortable: false,
                    editable: false,
                    cell: TemplateCell.extend({
                        template: "templates/openig/admin/apps/components/backgrid/AppNameCell.html"
                    })
                },
                {
                    name: "content/baseURI",
                    label: i18n.t("templates.apps.tableColumns.baseURI"),
                    cell: "string",
                    sortable: false,
                    editable: false
                },
                {
                    name: "status",
                    label: i18n.t("templates.apps.tableColumns.status"),
                    sortable: false,
                    editable: false,
                    cell: TemplateCell.extend({
                        template: "templates/openig/admin/apps/components/backgrid/AppStatusCell.html"
                    })
                },
                {
                    name: "",
                    sortable: false,
                    editable: false,
                    cell: TemplateCell.extend({
                        template: "templates/openig/admin/apps/components/backgrid/AppMenuCell.html"
                    })
                },
                {
                    className: "smallScreenCell renderable",
                    name: "smallScreenCell",
                    sortable: false,
                    editable: false,
                    cell: TemplateCell.extend({
                        template: "templates/openig/admin/apps/components/backgrid/AppSmallScreenCell.html"
                    })
                }
            ];

            const appsGrid = new Backgrid.Grid({
                className: "table backgrid",
                row: RenderRow,
                columns: tableColumns,
                collection: AppsCollection
            });

            this.data.docHelpUrl = externalLinks.backstage.admin.appsList;

            AppsCollection.availableApps().done((apps) => {
                RoutesCollection.fetchRoutesIds().done((routes) => {
                    if (routes) {
                        this.routesList = routes.models;
                        _.each(apps.models, (app) => {
                            const isDeployed = RoutesCollection.isDeployed(app.id);
                            let updatedContent = _.clone(app.get("content"));
                            updatedContent = _.extend(updatedContent, {
                                deployed: isDeployed,
                                pendingChanges: isDeployed ? updatedContent.pendingChanges : false
                            });
                            app.set("content", updatedContent);
                        });
                    }
                }).always(() => {
                    this.data.cardData = [];
                    _.each(apps.models, (app) => {
                        this.data.cardData.push(this.getRenderData(app));
                    });

                    this.parentRender(() => {
                        if (apps.length > 0) {
                            this.$el.find("#appsGrid").append(appsGrid.render().el);
                        } else {
                            this.renderNoItem();
                        }
                        if (callback) {
                            callback();
                        }
                    });
                });
            });
        },

        renderNoItem () {
            const noItemBox = new NoItemBox(
                {
                    route: "addAppView",
                    icon: "fa-rocket",
                    message: "templates.apps.noAppItems",
                    buttonText: "templates.apps.addApp"
                });
            noItemBox.element = ".noItemPlace";
            noItemBox.render();
        },

        getRenderData (model) {
            return {
                id: model.get("_id"),
                uri: model.get("content/baseURI"),
                name: model.get("content/name"),
                status: i18n.t(this.getStatusTextKey(
                    model.get("content/deployed") === true,
                    model.get("content/pendingChanges") === true)
                ),
                deployed: model.get("content/deployed"),
                pendingChanges: model.get("content/pendingChanges")
            };
        },

        duplicateAppConfig (event) {
            const itemData = this.getSelectedItemData(event);
            appsUtils.duplicateAppDialog(itemData.id, itemData.name);
        },

        deployApp (event) {
            const itemData = this.getSelectedItemData(event);
            appsUtils.deployApplicationDialog(itemData.id, itemData.name).done(() => {
                this.render();
            });
        },

        undeployApp (event) {
            const itemData = this.getSelectedItemData(event);
            appsUtils.undeployApplicationDialog(itemData.id, itemData.name).done(() => {
                this.render();
            });
        },

        deleteApps (event) {
            const itemData = this.getSelectedItemData(event);
            appsUtils.deleteApplicationDialog(itemData.id, itemData.name);
        },

        exportAppConfig (event) {
            const itemData = this.getSelectedItemData(event);
            appsUtils.exportConfigDialog(itemData.id, itemData.name);
        },

        /* Get selected item (card or row) */
        getSelectedItemData (event) {
            let selectedItem = $(event.currentTarget).parents(".card-spacer");
            if (!selectedItem.length) {
                selectedItem = $(event.currentTarget).parents("tr");
            }
            return selectedItem.data();
        },

        /* switch cards and grid */
        toggleButtonChange (event) {
            let target = $(event.target);
            this.saveListViewStyle(target.hasClass("fa-th") ? this.viewStyleTypes.card : this.viewStyleTypes.grid);

            if (target.hasClass("fa")) {
                target = target.parents(".btn");
            }

            this.$el.find(".toggle-view-btn").toggleClass("active", false);
            target.toggleClass("active", true);
        },

        saveListViewStyle (style) {
            const expire = new Date();
            expire.setDate(expire.getDate() + constants.viewSettingCookieExpiration);
            CookieHelper.setCookie(
                this.listStyleCookieName,
                style,
                expire
            );
        },

        getStatusTextKey (deployed, pendingChnages) {
            if (deployed === true) {
                if (pendingChnages === true) {
                    return "templates.apps.changesPending";
                } else {
                    return "templates.apps.deployedState";
                }
            } else {
                return "templates.apps.undeployedState";
            }
        },

        /* Filter cards and rows */
        filterApps (event) {
            const dataFilter = new DataFilter($(event.target).val(), ["id", "uri", "name", "status"]);
            _.each(this.$el.find(".app-item"), (elm) => {
                const element = $(elm);
                AppsCollection.byId(element.data("id"))
                    .then((model) => {
                        if (dataFilter.filter(this.getRenderData(model))) {
                            element.fadeIn();
                        } else {
                            element.fadeOut();
                        }
                    });
            });
        }
    });

    return new AppsListView();
});
