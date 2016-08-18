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
    "org/forgerock/openig/ui/common/util/ExternalLinks",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/util/AppsUtils",
    "backgrid",
    "org/forgerock/commons/ui/common/util/BackgridUtils",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/openig/ui/admin/models/AppsCollection",
    "org/forgerock/openig/ui/admin/models/RoutesCollection"
], (
    $,
    _,
    Backbone,
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
    RoutesCollection
) => {
    const AppsListView = AbstractView.extend({
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
        render (args, callback) {
           // const _this = this;

            // Render data attributes for click, context menu and filter
            const RenderRow = Backgrid.Row.extend({
                render () {
                    RenderRow.__super__.render.apply(this, arguments);
                    this.$el.attr("data-id", this.model.get("_id"));
                    this.$el.attr("data-url", this.model.get("content/url"));
                    this.$el.attr("data-title", this.model.get("content/name"));
                    this.$el.attr("data-deployed", this.model.deployed);
                    return this;
                }
            });

            this.data.docHelpUrl = externalLinks.backstage.admin.appsList;

            // Get Apps
            const appPromise = AppsCollection.availableApps();
            const routesPromise = RoutesCollection.fetch();
            this.data.routesCollection = RoutesCollection;

            $.when(appPromise, routesPromise).then(
                (apps) => {
                    this.routesList = this.data.routesCollection.models;
                    _.each(apps.models, (app) => {
                        app.deployed = this.data.routesCollection.isDeployed(app.id);
                    });
                    this.data.currentApps = apps.models;

                    this.parentRender(() => {
                        // TODO: use template cell instead of render method
                        const appsGrid = new Backgrid.Grid({
                            className: "table backgrid",
                            row: RenderRow,
                            columns: BackgridUtils.addSmallScreenCell([
                                {
                                    name: "name",
                                    sortable: false,
                                    editable: false,
                                    cell: Backgrid.Cell.extend({
                                        render () {
                                            const display = '<a class="table-clink" href="#apps/edit/' +
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
                                        render () {
                                            let display = "";

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
                                        render () {
                                            const display = $('<div class="btn-group pull-right">' +
                                                '<button type="button" class="btn btn-link fa-lg' +
                                                'dropdown-toggle" data-toggle="dropdown" aria-expanded="false">' +
                                                '<i class="fa fa-ellipsis-v"></i>' +
                                                "</button></div>");

                                            $(display).append(
                                                this.$el.find(
                                                    `[data-id='${this.model.get("_id")}'] .dropdown-menu`
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

                    });
                });
        },

        duplicateAppConfig (event) {
            const item = this.getSelectedItem(event);
            const itemTitle = item.selected.data("title");
            const itemId = item.selected.data("id");
            appsUtils.duplicateAppDlg(itemId, itemTitle);
        },

        deployApp (event) {
            const item = this.getSelectedItem(event);
            const itemTitle = item.selected.data("title");
            const itemId = item.selected.data("id");
            appsUtils.deployApplicationDlg(itemId, itemTitle);
        },

        undeployApp (event) {
            const item = this.getSelectedItem(event);
            const itemTitle = item.selected.data("title");
            const itemId = item.selected.data("id");
            appsUtils.undeployApplicationDlg(itemId, itemTitle);
        },

        deleteApps (event) {
            const item = this.getSelectedItem(event);
            const itemTitle = item.selected.data("title");
            const itemId = item.selected.data("id");
            appsUtils.deleteApplicationDlg(itemId, itemTitle,
                () => {
                    item.selected.remove();
                    if (item.alternate) {
                        item.alternate.remove();
                    }
                }
            );
        },

        exportAppConfig (event) {
            const item = this.getSelectedItem(event);
            const itemTitle = item.selected.data("title");
            const itemId = item.selected.data("id");
            appsUtils.exportConfigDlg(itemId, itemTitle);
        },

        /* Get selected item (card or row) */
        getSelectedItem (event) {
            let selectedItem = $(event.currentTarget).parents(".card-spacer");
            let alternateItem;

            if (selectedItem.length > 0) {
                _.each(this.$el.find(".backgrid tbody tr"), (row) => {
                    if ($(row).attr("data-id") === selectedItem.attr("data-id")) {
                        alternateItem = $(row);
                    }
                });
            } else {
                selectedItem = $(event.currentTarget).parents("tr");

                _.each(this.$el.find(".card-spacer"), (card) => {
                    if ($(card).attr("data-id") === selectedItem.attr("data-id")) {
                        alternateItem = $(card);
                    }
                });
            }
            return { selected: selectedItem, alternate: alternateItem };
        },

        /* switch cards and grid */
        toggleButtonChange (event) {
            let target = $(event.target);

            if (target.hasClass("fa")) {
                target = target.parents(".btn");
            }

            this.$el.find(".toggle-view-btn").toggleClass("active", false);
            target.toggleClass("active", true);
        },

        /* Filter cards and rows */
        filterApps (event) {
            const search = $(event.target).val().toLowerCase();

            if (search.length > 0) {
                _.each(this.$el.find(".card-spacer"), (card) => {
                    const deployedText = $(card).attr("data-deployed") === "true"
                        ? $.t("templates.apps.deployedState") : $.t("templates.apps.undeployedState");
                    if ($(card).attr("data-id").toLowerCase().indexOf(search) > -1 ||
                        $(card).attr("data-url").toLowerCase().indexOf(search) > -1 ||
                        $(card).attr("data-title").toLowerCase().indexOf(search) > -1 ||
                        deployedText.toLowerCase().indexOf(search) > -1) {
                        $(card).fadeIn();
                    } else {
                        $(card).fadeOut();
                    }
                }, this);

                _.each(this.$el.find(".backgrid tbody tr"), (row) => {
                    const deployedText = $(row).attr("data-deployed") === "true"
                         ? $.t("templates.apps.deployedState") : $.t("templates.apps.undeployedState");
                    if ($(row).attr("data-id").toLowerCase().indexOf(search) > -1 ||
                        $(row).attr("data-url").toLowerCase().indexOf(search) > -1 ||
                        $(row).attr("data-title").toLowerCase().indexOf(search) > -1 ||
                        deployedText.toLowerCase().indexOf(search) > -1) {
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
