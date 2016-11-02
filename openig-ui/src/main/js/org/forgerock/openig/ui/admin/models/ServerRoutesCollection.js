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
    "org/forgerock/openig/ui/admin/models/ServerRouteModel"
], (
    $,
    _,
    Backbone,
    Constants,
    ServerRouteModel
) => {
    /* Collection of routes */
    const ServerRoutesCollection = Backbone.Collection.extend({
        url: `${Constants.systemObjectsPath}/_router/routes`,
        model: ServerRouteModel,
        parse (response) {
            return response.result;
        },
        fetchRoutesIds () {
            return this.fetch({
                reset: true,
                processData: false,
                data: $.param({ _queryFilter: true, _fields: "_id" })
            });
        },
        isDeployed (id) {
            return _.find(this.models, (route) => (route.id === id)) !== undefined;
        },
        deploy (id, jsonConfig) {
            const deferred = $.Deferred();
            // Refresh list of deployed routes
            this.fetchRoutesIds()
                .then(() => {
                    let serverRoute = this.get(id);
                    if (!serverRoute) {
                        serverRoute = new ServerRouteModel(jsonConfig);
                        serverRoute.id = id;
                    } else {
                        serverRoute.set(jsonConfig);
                    }
                    serverRoute.save().success((deployResult) => {
                        if (serverRoute.isNew()) {
                            this.add(deployResult);
                        }
                        deferred.resolve();
                    }).error((error) => {
                        console.log(error);
                        deferred.reject(error);
                    });
                });
            return deferred;
        },
        undeploy (id) {
            const deferred = $.Deferred();
            const promise = deferred.promise();
            const model = this.get(id);
            if (model) {
                model.destroy().done((model) => {
                    this.remove(model);
                    deferred.resolve();
                }).fail((error) => {
                    console.log(error);
                    deferred.reject(error);
                });
            } else {
                deferred.reject();
            }
            return promise;
        }
    });

    return new ServerRoutesCollection();
});
