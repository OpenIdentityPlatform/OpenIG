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
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/AbstractDelegate",
    "org/forgerock/commons/ui/common/main/EventManager"
], ($, _, constants, AbstractDelegate) => {

    const obj = new AbstractDelegate(`${constants.host}/${constants.context}/api/system/objects/config`);

    obj.appsDelegateCache = {};

    obj.availableApps = function () {
        return obj.serviceCall({
            url: "",
            type: "POST"
        });
    };

    obj.detailsApp = function (appParams) {
        return obj.serviceCall({
            url: "?_action=createCoreConfig",
            type: "POST",
            data: JSON.stringify(appParams)
        });
    };

    obj.currentApps = function () {
        const deferred = $.Deferred();
        const promise = deferred.promise();

        if (obj.appsDelegateCache.currentApps) {
            deferred.resolve(_.clone(obj.appsDelegateCache.currentApps));
        } else {
            obj.serviceCall({
                url: "",
                type: "POST"
            }).then((result) => {
                obj.appsDelegateCache.currentApps = result;
                deferred.resolve(result);
            });
        }

        return promise;
    };

    obj.queryApps = function (name) {
        return obj.serviceCall({
            url: `/${name}?_queryId=query-all-ids`,
            type: "GET"
        });
    };

    obj.deleteCurrentAppsCache = function () {
        delete obj.appsDelegateCache.currentApps;
    };

    return obj;
});
