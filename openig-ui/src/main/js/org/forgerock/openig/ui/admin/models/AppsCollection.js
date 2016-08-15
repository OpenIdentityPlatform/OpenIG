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
], (
    $,
    _,
    Constants,
    AbstractCollection,
    AppModel
) => {
    /* Get and keep apps(=app configs)*/
    // TODO: Save to server
    const AppsCollection = AbstractCollection.extend({
        model: AppModel,
        url: "/openig/api/system/objects/config",

        appsCache: {},
        // Get all apps from server and save in local cache
        availableApps () {
            const deferred = $.Deferred();
            const promise = deferred.promise();

            if (this.appsCache.currentApps) {
                deferred.resolve(this.appsCache.currentApps);
            } else {
                const appPromise = this.fetch();
                appPromise.then(() => {
                    this.appsCache.currentApps = this;
                    deferred.resolve(this);
                });
            }

            return promise;
        },

        // Find by Id, also in cache
        byId (appId) {
            const deferred = $.Deferred();
            const promise = deferred.promise();

            this.availableApps().then(() => {
                deferred.resolve(this.get(appId));
            });

            return promise;
        },

            // Remove also from local cache
        removeById (appId) {
            const item = this.get(appId);
            item.destroy();
            this.remove(item);
            this.appsCache.currentApps.remove(item);
        }
    });

    return new AppsCollection();
});
