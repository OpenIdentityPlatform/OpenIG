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
    "backbone",
    "i18next",
    "bootstrap",
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/common/util/ExternalLinks",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/util/RoutesUtils",
    "backgrid",
    "org/forgerock/commons/ui/common/util/BackgridUtils",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/openig/ui/admin/models/RoutesCollection",
    "org/forgerock/openig/ui/admin/models/ServerRoutesCollection",
    "org/forgerock/openig/ui/admin/views/common/NoItemBox",
    "org/forgerock/commons/ui/common/util/CookieHelper",
    "org/forgerock/openig/ui/admin/util/DataFilter",
    "org/forgerock/openig/ui/admin/routes/parts/CardsList"
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
    RoutesUtils,
    Backgrid,
    BackgridUtils,
    UIUtils,
    RoutesCollection,
    ServerRoutesCollection,
    NoItemBox,
    CookieHelper,
    DataFilter,
    CardsList
) => {
    const RoutesListView = AbstractView.extend({
        template: "templates/openig/admin/routes/RoutesListViewTemplate.html",
        partials: [
            "templates/openig/admin/routes/components/RoutePopupMenu.html"
        ],
        events: {
            "click .route-export": "exportRouteConfig",
            "click .route-duplicate": "duplicateRoute",
            "click .route-deploy": "deployRoute",
            "click .route-undeploy": "undeployRoute",
            "click .route-delete": "deleteRoute",
            "click .toggle-view-btn": "toggleButtonChange",
            "keyup .filter-input": "routesFilterChanged",
            "paste .filter-input": "routesFilterChanged"
        },
        viewStyleTypes: { card: "card", grid: "grid" },
        listStyleCookieName: "openig-ui-routes-list-style",
        model: {

        },
        initialize () {
            AbstractView.prototype.initialize.call(this);
            this.listenTo(RoutesCollection, "change", this.renderItems);
        },
        render (args, callback) {
            const viewThis = this;

            // Get and refresh view style setting
            this.data.viewStyle = CookieHelper.getCookie(this.listStyleCookieName) || this.viewStyleTypes.card;
            this.saveListViewStyle(this.data.viewStyle);

            UIUtils.preloadPartial("templates/openig/admin/routes/components/RoutePopupMenu.html");
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
                        this.$el.attr("data-id", this.model.get("id"));
                        this.$el.attr("data-name", this.model.get("name"));
                        this.$el.addClass("route-item");
                    }
                    return this;
                }
            });

            const tableColumns = [
                {
                    name: "name",
                    label: i18n.t("templates.routes.tableColumns.name"),
                    sortable: false,
                    editable: false,
                    cell: TemplateCell.extend({
                        template: "templates/openig/admin/routes/components/backgrid/RouteNameCell.html"
                    })
                },
                {
                    name: "baseURI",
                    label: i18n.t("templates.routes.tableColumns.baseURI"),
                    cell: "string",
                    sortable: false,
                    editable: false
                },
                {
                    name: "status",
                    label: i18n.t("templates.routes.tableColumns.status"),
                    sortable: false,
                    editable: false,
                    cell: TemplateCell.extend({
                        template: "templates/openig/admin/routes/components/backgrid/RouteStatusCell.html"
                    })
                },
                {
                    name: "",
                    sortable: false,
                    editable: false,
                    cell: TemplateCell.extend({
                        template: "templates/openig/admin/routes/components/backgrid/RouteMenuCell.html"
                    })
                },
                {
                    className: "smallScreenCell renderable",
                    name: "smallScreenCell",
                    sortable: false,
                    editable: false,
                    cell: TemplateCell.extend({
                        template: "templates/openig/admin/routes/components/backgrid/RouteSmallScreenCell.html"
                    })
                }
            ];

            this.routesGrid = new Backgrid.Grid({
                className: "table backgrid",
                row: RenderRow,
                columns: tableColumns,
                collection: RoutesCollection
            });

            this.data.docHelpUrl = externalLinks.backstage.admin.routesList;

            RoutesCollection.availableRoutes().done((routes) => {
                ServerRoutesCollection.fetchRoutesIds().done((serverRoutes) => {
                    if (serverRoutes) {
                        this.routesList = serverRoutes.models;
                        _.each(routes.models, (route) => {
                            const isDeployed = ServerRoutesCollection.isDeployed(route.get("id"));
                            route.set({
                                deployed: isDeployed,
                                pendingChanges: isDeployed ? route.get("pendingChanges") : false
                            });
                        });
                    }
                });
            }).always(() => {
                this.parentRender(() => {
                    this.renderItems();
                    if (callback) {
                        callback();
                    }
                });
            });
        },

        renderNoItem () {
            const noItemBox = new NoItemBox(
                {
                    route: "addRouteView",
                    icon: "fa-rocket",
                    message: "templates.routes.noRouteItems",
                    buttonText: "templates.routes.addRoute"
                });
            noItemBox.element = ".noItemPlace";
            noItemBox.render();
        },

        getRenderData (model) {
            return {
                id: model.get("id"),
                uri: model.get("baseURI"),
                name: model.get("name"),
                status: i18n.t(model.getStatusTextKey()),
                deployed: model.get("deployed"),
                pendingChanges: model.get("pendingChanges")
            };
        },

        duplicateRoute (event) {
            event.preventDefault();
            const itemData = this.getSelectedItemData(event);
            RoutesUtils.duplicateRouteDialog(itemData.id, itemData.name);
        },

        deployRoute (event) {
            event.preventDefault();
            const itemData = this.getSelectedItemData(event);
            RoutesUtils.deployRouteDialog(itemData.id, itemData.name);
        },

        undeployRoute (event) {
            event.preventDefault();
            const itemData = this.getSelectedItemData(event);
            RoutesUtils.undeployRouteDialog(itemData.id, itemData.name);
        },

        deleteRoute (event) {
            event.preventDefault();
            const itemData = this.getSelectedItemData(event);
            RoutesUtils.deleteRouteDialog(itemData.id, itemData.name);
        },

        exportRouteConfig (event) {
            event.preventDefault();
            const itemData = this.getSelectedItemData(event);
            RoutesUtils.exportRouteConfigDialog(itemData.id, itemData.name);
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

        routesFilterChanged (event) {
            this.data.filter = $(event.target).val();
            this.filterRoutes(this.data.filter);
        },

        /* Filter cards and rows */
        filterRoutes (filterText) {
            const dataFilter = new DataFilter(filterText, ["id", "uri", "name", "status"]);
            _.each(this.$el.find(".route-item"), (elm) => {
                const element = $(elm);
                RoutesCollection.byRouteId(element.data("id"))
                    .then((model) => {
                        if (dataFilter.filter(this.getRenderData(model))) {
                            element.fadeIn();
                        } else {
                            element.fadeOut();
                        }
                    });
            });
        },

        /* Render Cards and Table */
        renderItems () {
            if (RoutesCollection.length === 0) {
                this.renderNoItem();
                return;
            }
            const cardsList = new CardsList({
                collection: RoutesCollection,
                getRenderData: this.getRenderData
            });
            cardsList.element = "#routeCardList";
            cardsList.render();
            this.$el.find("#routesGrid").append(this.routesGrid.render().el);
            if (this.data.filter) {
                this.filterRoutes(this.data.filter);
            }
        }
    });

    return new RoutesListView();
});
