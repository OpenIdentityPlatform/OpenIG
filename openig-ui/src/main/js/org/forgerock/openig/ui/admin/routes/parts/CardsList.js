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
    "org/forgerock/commons/ui/common/main/AbstractView"
], (
    $,
    _,
    AbstractView
) => (
    class extends AbstractView {

        get template () {
            return "templates/openig/admin/routes/components/RouteCardsList.html";
        }

        get partials () {
            return [
                "templates/openig/admin/routes/components/RouteCard.html",
                "templates/openig/admin/routes/components/RoutePopupMenu.html"
            ];
        }

        initialize (options) {
            this.data = {};
            this.collection = options.collection;
            this.getRenderData = options.getRenderData;
        }

        render () {
            this.data.cardData = [];
            _.each(this.collection.models, (route) => {
                this.data.cardData.push(this.getRenderData ? this.getRenderData(route) : route);
            });
            this.parentRender();
        }
    }
));
