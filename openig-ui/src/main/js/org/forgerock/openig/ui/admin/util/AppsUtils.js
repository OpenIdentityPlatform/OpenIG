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
    "org/forgerock/openig/ui/admin/delegates/AppDelegate"
], function (
    $,
    _,
    AppDelegate
) {
    var obj = {};

    obj.configPromise = null;

    obj.cleanAppName = function (name) {
        // TODO: add some checks, fixes
        var clearName = name;
        return clearName;
    };

    obj.getMappingDetails = function (sourceName, targetName) {
        var iconList = obj.getIconList(),
            currentConnectors = AppDelegate.currentApps(),
            deferred = $.Deferred(),
            details = null;

        $.when(iconList, currentConnectors).then(function (icons, connectors) {
            details = {};

            if (targetName !== "managed") {
                details.targetConnector = _.find(connectors, function (connector) {
                    return connector.name === targetName;
                }, this);

                details.targetIcon = obj.getIcon(details.targetConnector.connectorRef.connectorName, icons);
            } else {
                details.targetConnector = null;
                details.targetIcon = obj.getIcon("managedobject", icons);
            }

            if (sourceName !== "managed") {
                details.sourceConnector = _.find(connectors, function (connector) {
                    return connector.name === sourceName;
                }, this);

                details.sourceIcon = obj.getIcon(details.sourceConnector.connectorRef.connectorName, icons);
            } else {
                details.sourceConnector = null;
                details.sourceIcon = obj.getIcon("managedobject", icons);
            }

            details.sourceName = sourceName;
            details.targetName = targetName;

            deferred.resolve(details);
        });

        return deferred;
    };

    obj.toggleValue = function (e) {
        var toggle = this.$el.find(e.target);
        if (toggle.val() === "true") {
            toggle.val(false);
        } else {
            toggle.val(true);
        }
    };

    obj.exportConfig = function (/*appId*/) {
        // TODO: call export function
    };

    obj.deployApplication = function (/*appId*/) {
        // TODO: call app config to real config conversion
        // TODO: send real config to router endpoint
    };

    obj.undeployApplication = function (/*appId*/) {
        // TODO: call router endpoint
    };

    return obj;
});
