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
    "org/forgerock/openig/ui/admin/models/AppModel"
], (
    $,
    Backbone,
    Constants,
    AppModel
) => {
    /* Get and keep apps(=app configs)*/
    class AppsCollection extends Backbone.Collection {
        constructor () {
            super();
            this.url = `${Constants.apiPath}/ui/record`;
            this.model = AppModel;
            this.appsCache = {};
        }

        parse (response) {
            return response.result;
        }

        // Get all apps from server and save in local cache
        availableApps () {
            const deferred = $.Deferred();
            if (this.appsCache.currentApps) {
                deferred.resolve(this.appsCache.currentApps);
            } else {
                this.fetch({
                    reset: true,
                    processData: false,
                    data: $.param({ _queryFilter: true })
                })
                .then(
                    () => {
                        this.appsCache.currentApps = this;
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
        byAppId (id) {
            const deferred = $.Deferred();
            this.availableApps().then(() => {
                deferred.resolve(this.findWhere({ id }));
            });

            return deferred;
        }

        // Remove also from local cache
        removeByAppId (id) {
            const deferred = $.Deferred();
            const item = this.findWhere({ id });
            item.destroy()
                .then(
                    (model) => {
                        this.remove(model);
                        this.appsCache.currentApps.remove(item);
                        deferred.resolve();
                    },
                    (error) => {
                        console.log(error);
                        deferred.reject(error);
                    }
                );
            return deferred;
        }
    }

    return new AppsCollection();
});
