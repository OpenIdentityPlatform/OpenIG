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
    "org/forgerock/commons/ui/common/util/Constants"
], (commonConstants) => {

    commonConstants.context = "openig";

    commonConstants.apiPath = "/openig/api";

    commonConstants.systemObjectsPath = `${commonConstants.apiPath}/system/objects`;

    commonConstants.DOC_URL = "https://backstage.forgerock.com/#!/docs/openig/5.0/";

    commonConstants.defaultFiltersOrder = {
        ThrottlingFilter: 0,
        OAuth2ClientFilter: 100,
        PolicyEnforcementFilter: 200,
        Unknown: 10000
    };

    commonConstants.timeSlot = {
        NANOSECOND: "ns",
        MICROSECOND: "us",
        MILLISECOND: "ms",
        SECOND: "s",
        MINUTE: "m",
        HOUR: "h",
        DAY: "d"
    };

    commonConstants.studioUser = "studio";

    // view setting cookie expiration in days
    commonConstants.viewSettingCookieExpiration = 30;

    return commonConstants;
});
