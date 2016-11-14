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
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/openig/ui/common/util/Constants"
], (
    $,
    _,
    AbstractView,
    EventManager,
    Constants
) => {
    const AbstractRouteView = AbstractView.extend({
        element: ".main",
        NOTIFICATION_TYPE: {
            SaveSuccess: "routeSettingsSaveSuccess",
            SaveFailed: "routeSettingsSaveFailed",
            Disabled: "routeSettingsDisabled"
        },
        showNotification (msgKey) {
            EventManager.sendEvent(
                Constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                {
                    key: msgKey,
                    filter: this.settingTitle
                }
            );
        }
    });

    return AbstractRouteView;
});
