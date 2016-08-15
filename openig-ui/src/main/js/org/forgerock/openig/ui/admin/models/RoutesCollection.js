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
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/AbstractCollection"
], (
    $,
    _,
    Backbone,
    Constants,
    AbstractCollection
) => {
    /* Get routes from default router(TODO: different routers)*/
    const routeModel = Backbone.Model.extend({});
    const RoutesCollection = AbstractCollection.extend({
        model: routeModel,
        url: "/openig/api/system/objects/router-handler/routes"
    });

    const routesCollection = new RoutesCollection();

    routesCollection.isDeployed = function (appId) {
        return _.find(this.models, (route) => (route.get("_id") === appId)) !== undefined;
    };

    return routesCollection;
});
