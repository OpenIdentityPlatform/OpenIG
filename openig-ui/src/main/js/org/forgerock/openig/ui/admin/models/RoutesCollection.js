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
    "backbone",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/models/RouteModel"
], (
    $,
    Backbone,
    Constants,
    RouteModel
) => {
    /* Get and keep Routes */
    class RoutesCollection extends Backbone.Collection {
        constructor () {
            super();
            this.url = `${Constants.systemObjectsPath}/ui/record`;
            this.model = RouteModel;
            this.routesCache = {};
        }

        parse (response) {
            return response.result;
        }

        // Get all routes from server and save in local cache
        availableRoutes () {
            const deferred = $.Deferred();
            if (this.routesCache.currentRoutes) {
                deferred.resolve(this.routesCache.currentRoutes);
            } else {
                this.fetch({
                    reset: true,
                    processData: false,
                    data: $.param({ _queryFilter: true })
                })
                .then(
                    () => {
                        this.routesCache.currentRoutes = this;
                        deferred.resolve(this);
                    },
                    () => {
                        deferred.reject();
                    }
                );
            }

            return deferred;
        }

        // Find by Id, also in cache
        byRouteId (id) {
            const deferred = $.Deferred();
            this.availableRoutes().then(() => {
                deferred.resolve(this.findWhere({ id }));
            });

            return deferred;
        }

        // Remove also from local cache
        removeByRouteId (id) {
            const deferred = $.Deferred();
            const item = this.findWhere({ id });
            item.destroy()
                .then(
                    (model) => {
                        this.remove(model);
                        this.routesCache.currentRoutes.remove(item);
                        deferred.resolve();
                    },
                    (error) => {
                        console.log(error);
                        deferred.reject(error);
                    }
                );
            return deferred;
        }

        comparator (item) {
            // Sort by name
            return item.get("name");
        }
    }

    return new RoutesCollection();
});
