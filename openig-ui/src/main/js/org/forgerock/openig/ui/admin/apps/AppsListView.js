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
    "bootstrap",
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/util/AppsUtils",
    "backgrid",
    "org/forgerock/commons/ui/common/util/BackgridUtils",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/openig/ui/admin/models/AppsCollection",
    "org/forgerock/openig/ui/admin/models/RoutesCollection"
], function (
    $,
    _,
    Backbone,
    bootstrap,
    AbstractView,
    eventManager,
    constants,
    router,
    appsUtils,
    Backgrid,
    BackgridUtils,
    UIUtils,
    AppsCollection,
    RoutesCollection
) {
    var AppsListView = AbstractView.extend({
        template: "templates/openig/admin/apps/AppsListViewTemplate.html",
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
        model: {

        },
        render: function (args, callback) {
            var appPromise,
                routesPromise,
                appsGrid,
                RenderRow = null,
                _this = this;

            // Render data attributes for click, context menu and filter
            RenderRow = Backgrid.Row.extend({
                render: function () {
                    RenderRow.__super__.render.apply(this, arguments);
                    this.$el.attr("data-id", this.model.get("_id"));
                    this.$el.attr("data-url", this.model.get("content/url"));
                    this.$el.attr("data-title", this.model.get("content/name"));
                    return this;
                }
            });

            this.data.docHelpUrl = constants.DOC_URL;

            // Get Apps
            appPromise = AppsCollection.availableApps();
            routesPromise = RoutesCollection.fetch();
            this.data.routesCollection = RoutesCollection;

            $.when(appPromise, routesPromise).then(
                _.bind(function (apps) {
                    this.routesList = this.data.routesCollection.models;
                    _.each(apps.models, _.bind(function (app) {
                        app.deployed = this.data.routesCollection.isDeployed(app.id);
                    }, this));
                    this.data.currentApps = apps.models;

                    this.parentRender(_.bind(function () {
                        appsGrid = new Backgrid.Grid({
                            className: "table backgrid",
                            row: RenderRow,
                            columns: BackgridUtils.addSmallScreenCell([
                                {
                                    name: "name",
                                    sortable: false,
                                    editable: false,
                                    cell: Backgrid.Cell.extend({
                                        render: function () {
                                            var display = '<a class="table-clink" href="#apps/edit/' +
                                                this.model.get("_id") + '/"><div class="image circle">' +
                                                '<i class="fa fa-rocket"></i></div>' +
                                                this.model.get("content/name") +
                                                "</a>";
                                            this.$el.html(display);

                                            return this;
                                        }
                                    })
                                },
                                {
                                    name: "content/url",
                                    label: "url",
                                    cell: "string",
                                    sortable: false,
                                    editable: false
                                },
                                {
                                    name: "status",
                                    label: "status",
                                    sortable: false,
                                    editable: false,
                                    cell: Backgrid.Cell.extend({
                                        render: function () {
                                            var display = "";

                                            if (this.model.deployed) {
                                                display = '<span class="text-success">' +
                                                    '<i class="fa fa-check-circle"></i> ' +
                                                    $.t("templates.apps.deployedState") +
                                                    "</span>";
                                            } else {
                                                display = '<span class="text-danger resource-unavailable">' +
                                                    '<i class="fa fa-exclamation-circle"></i> ' +
                                                    $.t("templates.apps.undeployedState") +
                                                    "</span>";
                                            }

                                            this.$el.html(display);
                                            return this;
                                        }
                                    })
                                },
                                {
                                    name: "",
                                    sortable: false,
                                    editable: false,
                                    cell: Backgrid.Cell.extend({
                                        render: function () {
                                            var display = $('<div class="btn-group pull-right">' +
                                                '<button type="button" class="btn btn-link fa-lg' +
                                                'dropdown-toggle" data-toggle="dropdown" aria-expanded="false">' +
                                                '<i class="fa fa-ellipsis-v"></i>' +
                                                "</button></div>");

                                            $(display).append(
                                                _this.$el.find(
                                                    "[data-id='" + this.model.get("_id") + "'] .dropdown-menu"
                                                ).clone());

                                            this.$el.html(display);

                                            return this;
                                        }
                                    })
                                }
                            ]),
                            collection: apps
                        });


                        this.$el.find("#appsGrid").append(appsGrid.render().el);

                        if (callback) {
                            callback();
                        }

                    }, this));
                }, this));
        },

        duplicateAppConfig: function (event) {
            var item = this.getSelectedItem(event),
                itemTitle = item.selected.data("title"),
                itemId = item.selected.data("id");

            UIUtils.confirmDialog($.t("templates.apps.duplicateDialog", { title: itemTitle }), "danger",
                _.bind(function () {
                    this.data.currentApp = { title: itemId };
                    router.navigate("apps/duplicate/" + itemId, true);
                }, this)
            );
        },

        deployApp: function (event) {
            var item = this.getSelectedItem(event),
                itemTitle = item.selected.data("title"),
                itemId = item.selected.data("id");
            UIUtils.confirmDialog($.t("templates.apps.deployDialog", { title: itemTitle }), "danger",
                _.bind(function () {
                    appsUtils.deployApplication(itemId);
                }, this)
            );
        },

        undeployApp: function (event) {
            var item = this.getSelectedItem(event),
                itemTitle = item.selected.data("title"),
                itemId = item.selected.data("id");
            UIUtils.confirmDialog($.t("templates.apps.undeployDialog", { title: itemTitle }), "danger",
                _.bind(function () {
                    appsUtils.undeployApplication(itemId);
                }, this)
            );
        },

        deleteApps: function (event) {
            var item = this.getSelectedItem(event),
                itemTitle = item.selected.data("title"),
                itemId = item.selected.data("id");
            UIUtils.confirmDialog($.t("templates.apps.deleteDialog", { title: itemTitle }), "danger",
                _.bind(function () {
                    AppsCollection.removeById(itemId);

                    item.selected.remove();
                    if (item.alternate) {
                        item.alternate.remove();
                    }
                    eventManager.sendEvent(
                        constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                        { key: "deleteAppSuccess", title: itemTitle }
                    );
                }, this)
            );
        },

        exportAppConfig: function (event) {
            var item = this.getSelectedItem(event),
                itemId = item.selected.data("id");
            appsUtils.exportConfig(itemId);
        },

        /* Get selected item (card or row) */
        getSelectedItem: function (event) {
            var selectedItem = $(event.currentTarget).parents(".card-spacer"),
                alternateItem;

            if (selectedItem.length > 0) {
                _.each(this.$el.find(".backgrid tbody tr"), function (row) {
                    if ($(row).attr("data-id") === selectedItem.attr("data-id")) {
                        alternateItem = $(row);
                    }
                });
            } else {
                selectedItem = $(event.currentTarget).parents("tr");

                _.each(this.$el.find(".card-spacer"), function (card) {
                    if ($(card).attr("data-id") === selectedItem.attr("data-id")) {
                        alternateItem = $(card);
                    }
                });
            }
            return { selected: selectedItem, alternate: alternateItem };
        },

        /* switch cards and grid */
        toggleButtonChange: function (event) {
            var target = $(event.target);

            if (target.hasClass("fa")) {
                target = target.parents(".btn");
            }

            this.$el.find(".toggle-view-btn").toggleClass("active", false);
            target.toggleClass("active", true);
        },

        /* Filter cards and rows */
        filterApps: function (event) {
            var search = $(event.target).val().toLowerCase();

            if (search.length > 0) {
                _.each(this.$el.find(".card-spacer"), function (card) {
                    if ($(card).attr("data-id").toLowerCase().indexOf(search) > -1 ||
                        $(card).attr("data-url").toLowerCase().indexOf(search) > -1 ||
                        $(card).attr("data-title").toLowerCase().indexOf(search) > -1) {
                        $(card).fadeIn();
                    } else {
                        $(card).fadeOut();
                    }
                }, this);

                _.each(this.$el.find(".backgrid tbody tr"), function (row) {
                    if ($(row).attr("data-id").toLowerCase().indexOf(search) > -1 ||
                        $(row).attr("data-url").toLowerCase().indexOf(search) > -1 ||
                        $(row).attr("data-title").toLowerCase().indexOf(search) > -1) {
                        $(row).fadeIn();
                    } else {
                        $(row).fadeOut();
                    }
                }, this);
            } else {
                this.$el.find(".card-spacer").fadeIn();
                this.$el.find(".backgrid tbody tr").fadeIn();
            }
        }
    });

    return new AppsListView();
});
