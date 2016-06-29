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
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/AbstractCollection",
    "org/forgerock/openig/ui/admin/models/AppModel"
], function (
    $,
    _,
    Constants,
    AbstractCollection,
    AppModel
) {
    /* Get and keep apps(=app configs)*/
    // TODO: Save to server
    var appsCollection,
        AppsCollection = AbstractCollection.extend({
            model: AppModel,
            url: "/openig/api/system/objects/config"
        });

    appsCollection = new AppsCollection();
    appsCollection.appsCache = {};

    // Get all apps from server and save in local cache
    appsCollection.availableApps = function () {
        var deferred = $.Deferred(),
            promise = deferred.promise(),
            appPromise;

        if (appsCollection.appsCache.currentApps) {
            deferred.resolve(appsCollection.appsCache.currentApps);
        } else {
            appPromise = appsCollection.fetch();
            appPromise.then(function () {
                appsCollection.appsCache.currentApps = appsCollection;
                deferred.resolve(appsCollection);
            });
        }

        return promise;
    };

        // Find by Id, also in cache
    appsCollection.byId = function (appId) {
        var deferred = $.Deferred(),
            promise = deferred.promise();

        appsCollection.availableApps().then(function () {
            deferred.resolve(appsCollection._byId[appId]);
        });

        return promise;
    };

        // Remove also from local cache
    appsCollection.removeById = function (appId) {
        var item = appsCollection._byId[appId];
        item.destroy();
        appsCollection.remove(item);
        appsCollection.appsCache.currentApps.remove(item);
    };

    return appsCollection;
});
