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
    "backbone",
    "org/forgerock/commons/ui/common/main/AbstractModel"
], (
    $,
    _,
    Backbone,
    AbstractModel
) => {
    /* Define Settings structure */

    //var mockPrefix = "mock/repo/internal/AppModel/";
    const SettingsModel = AbstractModel.extend({
        defaults: {
            _id: "",
            admin: {
                "port_network": "",
                "https": ""
            },
            functional: {
                "port_network": "",
                "https": ""
            }
        },

        validate (attrs) {
            if (!attrs._id || attrs._id.trim() === "") {
                return "errorNoId";
            }
            if (!attrs.admin || !attrs.admin.port_network || attrs.admin.port_network.trim() === "") {
                return "errorNoAdminPort_network";
            }
            if (!attrs.admin || !attrs.admin.https || attrs.admin.https.trim() === "") {
                return "errorNoAdminHttps";
            }
            if (!attrs.functional || !attrs.functional.port_network || attrs.functional.port_network.trim() === "") {
                return "errorNoFunctionalPort_network";
            }
            if (!attrs.functional || !attrs.functional.https || attrs.functional.https.trim() === "") {
                return "errorNoFunctionalHttps";
            }
        },

        // sync has to be overridden to work with localstorage; products using CREST backend shouldn't need to do so
        // TODO: solve Sync method
        sync (method) {
            switch (method) {
                case "read":
                    //model.set(LocalStorage.get(mockPrefix + model.id));
                    ///return $.Deferred().resolve(model.toJSON());
                    return null;

                case "patch":
                    var deferred = $.Deferred();
                    return deferred.promise();
            }
        }

    });
    return SettingsModel;
});
