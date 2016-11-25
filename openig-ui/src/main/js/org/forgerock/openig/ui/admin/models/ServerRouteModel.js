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
    "org/forgerock/openig/ui/admin/services/ServerUrls"
], (
    Backbone,
    serverUrls
) => (
    Backbone.Model.extend({
        url: `${serverUrls.systemObjectsPath}/_router/routes`,
        idAttribute: "_id",

        getMVCCRev () {
            return this.get("_rev") || "*";
        },

        sync (method, model, options) {
            options = options || {};
            options.url = `${this.url}/${model.id}`;
            if (method === "create") {
                options.type = "PUT";
                options.headers = { "If-None-Match": "*" };
            } else {
                options.headers = { "If-Match": model.getMVCCRev() };
            }

            options.headers["Cache-Control"] = "no-cache";
            options.dataType = "json";
            options.contentType = "application/json";

            return Backbone.Model.prototype.sync.call(this, method, model, options);
        }
    })
));
