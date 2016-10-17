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
    "org/forgerock/commons/ui/common/main/AbstractModel",
    "org/forgerock/openig/ui/common/util/Constants"
], (
    $,
    _,
    Backbone,
    AbstractModel,
    Constants
    ) => {
        /* Define App structure + add defaults, constants, orders */

    const AppModel = AbstractModel.extend({
        url: `${Constants.apiPath}/config`,
        defaults: {

            id: "",
            name: "",
            baseURI: "",
            condition: "",

            router: "",
            route: "",
            deployedDate: undefined,
            pendingChanges: false,

            // Order is as defined by user in "chain"
            filters: []

        },

        validate (attrs) {
            if (attrs.id.trim() === "") {
                return "appErrorNoId";
            }

            if (!attrs.name || attrs.name.trim() === "") {
                return "appErrorNoName";
            }

            if (!attrs.baseURI || attrs.baseURI.trim() === "") {
                return "appErrorNoUrl";
            }

            if (!attrs.condition || attrs.condition.trim() === "") {
                return "appErrorNoCondition";
            }

        }
    });
    return AppModel;
});
