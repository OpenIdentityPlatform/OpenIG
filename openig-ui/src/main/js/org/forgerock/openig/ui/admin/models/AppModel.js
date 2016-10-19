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
    "backbone",
    "org/forgerock/openig/ui/common/util/Constants"
], (
    Backbone,
    Constants
) => (
    /* Define App structure + add defaults, constants, orders */
    class extends Backbone.Model {
        constructor (options) {
            super(options);
            this.url = `${Constants.apiPath}/ui/record`;
        }

        get idAttribute () { return "_id"; }

        get defaults () {
            return {
                id: "",
                name: "",
                baseURI: "",
                condition: "",
                deployedDate: undefined,
                pendingChanges: false,
                filters: []
            };
        }

        validate (attrs) {
            if (!attrs.id || attrs.id.trim() === "") {
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

        getMVCCRev () {
            return this.get("_rev") || "*";
        }

        sync (method, model, options) {
            options = options || {};

            options.headers = {};
            if (method !== "create") {
                options.url = `${this.url}/${model.id}`;
                options.headers = { "If-Match": model.getMVCCRev() };
            }

            options.headers["Cache-Control"] = "no-cache";
            options.dataType = "json";
            options.contentType = "application/json";

            return Backbone.Model.prototype.sync.call(this, method, model, options);
        }
    }
));
