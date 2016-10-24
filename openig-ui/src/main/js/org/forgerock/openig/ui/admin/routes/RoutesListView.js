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
    RoutesUtils,
    Backgrid,
    BackgridUtils,
    UIUtils,
    RoutesCollection,
    ServerRoutesCollection,
    NoItemBox,
    CookieHelper,
    DataFilter
) => {
    const RoutesListView = AbstractView.extend({
        template: "templates/openig/admin/routes/RoutesListViewTemplate.html",
        partials: [
            "templates/openig/admin/routes/components/RouteCard.html",
            "templates/openig/admin/routes/components/RoutePopupMenu.html"
        ],
        events: {
            "click .route-export": "exportRouteConfig",
            "click .route-duplicate": "duplicateRouteConfig",
            "click .route-deploy": "deployRoute",
            "click .route-undeploy": "undeployRoute",
            "click .route-delete": "deleteRoute",
            "click .toggle-view-btn": "toggleButtonChange",
            "keyup .filter-input": "filterRoutes",
            "paste .filter-input": "filterRoutes"
        },
        viewStyleTypes: { card: "card", grid: "grid" },
        listStyleCookieName: "openig-ui-routes-list-style",
        model: {

        },
        initialize () {
            AbstractView.prototype.initialize.call(this);
            this.listenTo(RoutesCollection, "remove", this.removeItem);
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

            const routesGrid = new Backgrid.Grid({
                className: "table backgrid",
                row: RenderRow,
                columns: tableColumns,
                collection: RoutesCollection
            });

            this.data.docHelpUrl = externalLinks.backstage.admin.routesList;
            this.data.cardData = [];

            RoutesCollection.availableRoutes().done((routes) => {
                ServerRoutesCollection.fetchRoutesIds().done((serverRoutes) => {
                    if (serverRoutes) {
                        this.routesList = serverRoutes.models;
                        _.each(routes.models, (route) => {
                            const isDeployed = ServerRoutesCollection.isDeployed(route.get("id"));
                            route.set("deployed", isDeployed);
                            route.set("pendingChanges", isDeployed ? route.get("pendingChanges") : false);
                        });
                    }
                });
            }).always((routes) => {
                if (routes && routes.length > 0) {
                    _.each(routes.models, (route) => {
                        this.data.cardData.push(this.getRenderData(route));
                    });
                    this.parentRender(() => {
                        this.$el.find("#routesGrid").append(routesGrid.render().el);
                        if (callback) {
                            callback();
                        }
                    });
                } else {
                    this.parentRender(() => {
                        this.renderNoItem();
                        if (callback) {
                            callback();
                        }
                    });
                }
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
                status: i18n.t(this.getStatusTextKey(
                    model.get("deployed") === true,
                    model.get("pendingChanges") === true)
                ),
                deployed: model.get("deployed"),
                pendingChanges: model.get("pendingChanges")
            };
        },

        duplicateRoute (event) {
            const itemData = this.getSelectedItemData(event);
            RoutesUtils.duplicateRouteDialog(itemData.id, itemData.name);
        },

        deployRoute (event) {
            const itemData = this.getSelectedItemData(event);
            RoutesUtils.deployRouteDialog(itemData.id, itemData.name).done(() => {
                this.render();
            });
        },

        undeployRoute (event) {
            const itemData = this.getSelectedItemData(event);
            RoutesUtils.undeployRouteDialog(itemData.id, itemData.name).done(() => {
                this.render();
            });
        },

        deleteRoute (event) {
            const itemData = this.getSelectedItemData(event);
            RoutesUtils.deleteRouteDialog(itemData.id, itemData.name);
        },

        exportRouteConfig (event) {
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

        getStatusTextKey (deployed, pendingChnages) {
            if (deployed === true) {
                if (pendingChnages === true) {
                    return "templates.routes.changesPending";
                } else {
                    return "templates.routes.deployedState";
                }
            } else {
                return "templates.routes.undeployedState";
            }
        },

        /* Filter cards and rows */
        filterRoutes (event) {
            const dataFilter = new DataFilter($(event.target).val(), ["id", "uri", "name", "status"]);
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

        removeItem (model, collection) {
            const card = this.$el.find(`.card-spacer[data-route-id='${model.get("id")}']`);
            card.remove();

            if (collection.models.length === 0) {
                this.renderNoItem();
            }
        }
    });

    return new RoutesListView();
});
