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
 * Copyright 2014-2016 ForgeRock AS.
 */

/*global define, require, QUnit, localStorage, Backbone, _ */

define([
    "jquery",
    "doTimeout",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/EventManager",
    "../test/tests/OpenIGValidatorsTests",
    "../test/tests/getLoggedUser"
], function (
    $,
    doTimeout,
    constants,
    eventManager,
    openIGValidatorsTests,
    getLoggedUser) {

    $.doTimeout = function (name, time, func) {
        func(); // run the function immediately rather than delayed.
    };

    return function (server) {

        eventManager.registerListener(constants.EVENT_APP_INITIALIZED, function () {
            require("ThemeManager").getTheme().then(function () {
                QUnit.testStart(function (testDetails) {
                    console.log("Starting " + testDetails.module +
                        ":" + testDetails.name + "(" + testDetails.testNumber + ")");

                    var vm = require("org/forgerock/commons/ui/common/main/ViewManager");

                    vm.currentView = null;
                    vm.currentDialog = null;
                    vm.currentViewArgs = null;
                    vm.currentDialogArgs = null;

                    require("org/forgerock/commons/ui/common/main/Configuration").baseTemplate = null;
                });


                _.delay(function () {
                    QUnit.start();
                    openIGValidatorsTests.executeAll(server, getLoggedUser());
                }, 500);

                QUnit.done(function () {
                    localStorage.clear();
                    Backbone.history.stop();
                    window.location.hash = "";
                });
            });
        });
    };

});
