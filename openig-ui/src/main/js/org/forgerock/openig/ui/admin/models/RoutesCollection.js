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
    "org/forgerock/openig/ui/admin/models/RouteModel"
], (
    $,
    _,
    Backbone,
    Constants,
    RouteModel
) => {
    /* Collection of routes */
    const RoutesCollection = Backbone.Collection.extend({
        url: `${Constants.apiPath}/_router/routes`,
        model: RouteModel,
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
        isDeployed (appId) {
            return _.find(this.models, (route) => (route.id === appId)) !== undefined;
        },
        deploy (appId, jsonConfig) {
            const deferred = $.Deferred();
            const promise = deferred.promise();
            const route = new RouteModel(jsonConfig);
            route.id = appId;
            route.save().success((deployResult) => {
                this.add(deployResult);
                deferred.resolve();
            }).error((error) => {
                console.log(error);
                deferred.reject(error);
            });
            return promise;
        },
        undeploy (appId) {
            const deferred = $.Deferred();
            const promise = deferred.promise();
            const model = this.get(appId);
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

    return new RoutesCollection();
});
