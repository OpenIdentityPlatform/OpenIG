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
    "org/forgerock/openig/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/services/ServerInfoService"
], (
    $,
    _,
    Backbone,
    Constants,
    server
) => (
    /* Define Route structure + add defaults, constants, orders */
    class RouteModel extends Backbone.Model {
        constructor (options) {
            super(options);
            this.url = `${Constants.systemObjectsPath}/ui/record`;
        }

        get idAttribute () { return "_id"; }

        get defaults () {
            return {
                id: "",
                name: "",
                baseURI: "",
                deployedDate: undefined,
                pendingChanges: false,
                filters: []
            };
        }

        /**
         * Builds a new RouteModel that will be enriched with server version information.
         * @param {Object} options default values
         * @returns {Promise.<RouteModel>} a promise of a RouteModel
         */
        static newRouteModel (options) {
            return server.getInfo().then((info) => {
                const model = new RouteModel(options);
                if (info) {
                    model.set("version", info.version);
                }
                return model;
            });
        }

        validate (attrs) {
            if (!attrs.id || attrs.id.trim() === "") {
                return "routeErrorNoId";
            }

            if (!attrs.name || attrs.name.trim() === "") {
                return "routeErrorNoName";
            }

            if (!attrs.baseURI || attrs.baseURI.trim() === "") {
                return "routeErrorNoUrl";
            }
        }

        getMVCCRev () {
            return this.get("_rev") || "*";
        }

        save (attr, options) {
            this.setPendingChanges();
            return Backbone.Model.prototype.save.call(this, attr, options);
        }

        sync (method, model, options) {
            options = options || {};

            options.headers = {};
            if (method !== "create") {
                options.url = `${this.url}/${model.id}`;
                options.headers = { "If-Match": model.getMVCCRev() };
            }

            options.headers["Cache-Control"] = "no-cache";
            options.dataType = "json";
            options.contentType = "application/json";

            return Backbone.Model.prototype.sync.call(this, method, model, options);
        }

        getStatusTextKey () {
            if (this.get("deployed") === true) {
                if (this.get("pendingChanges") === true) {
                    return "templates.routes.changesPending";
                } else {
                    return "templates.routes.deployedState";
                }
            } else {
                return "templates.routes.undeployedState";
            }
        }

        setPendingChanges () {
            if (this.needUnsetPendingChanges()) {
                // model state changed to undeployed and has pending changes flag
                this.set("pendingChanges", false);
                console.log("No pending changes", this.id);
            } else if (this.needSetPendingChanges()) {
                // deployed model changed; ignore pendingChanges and deployedDate changes
                this.set("pendingChanges", true);
                console.log("Has pending changes", this.id);
            }
        }

        needUnsetPendingChanges () {
            return this.hasChanged("deployed") && this.get("deployed") && this.get("pendingChanges");
        }

        needSetPendingChanges () {
            return (!this.hasChanged("pendingChanges") && !this.hasChanged("deployedDate") &&
                this.get("deployed") && !this.get("pendingChanges"));
        }

        getFilter (condition) {
            return _.find(this.get("filters"), condition);
        }

        setFilter (filter, condition) {
            const filters = this.get("filters");
            const filterIndex = _.findIndex(filters, condition);
            filters[filterIndex] = filter;
            this.set("filters", filters);
            const hasChanged = !_.isEqual(this.get("filters"), this.previousAttributes().filters);
            if (hasChanged && this.needSetPendingChanges()) {
                this.set("pendingChanges", true);
                console.log("Has pending changes (filters)", this.id);
            }
        }
    }
));
