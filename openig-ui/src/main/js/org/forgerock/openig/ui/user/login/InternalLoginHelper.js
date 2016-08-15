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
 * Copyright 2011-2016 ForgeRock AS.
 */

define([
    "jquery",
    "org/forgerock/openig/ui/user/UserModel",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/AbstractConfigurationAware",
    "org/forgerock/commons/ui/common/main/ServiceInvoker",
    "org/forgerock/commons/ui/common/main/Configuration"
], ($, UserModel, eventManager, constants, AbstractConfigurationAware, serviceInvoker, conf) => {
    const obj = new AbstractConfigurationAware();

    obj.login = function (params, successCallback, errorCallback) {
        return UserModel.getProfile(params.userName, params.password).then(successCallback, errorCallback);
    };

    obj.logout = function (successCallback) {
        delete conf.loggedUser;
        if (successCallback) {
            successCallback();
        }
        return $.Deferred().resolve();
    };

    obj.getLoggedUser = function (successCallback, errorCallback) {
        // the mock project doesn't support sessions, so there is no point in checking
        if (conf.loggedUser && successCallback) {
            successCallback(conf.loggedUser);
        } else if (errorCallback) {
            errorCallback();
        }
    };
    return obj;
});
