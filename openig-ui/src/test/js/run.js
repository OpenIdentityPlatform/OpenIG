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
 * Portions Copyright 2026 3A Systems, LLC.
 */

/*global define, require, QUnit, localStorage, Backbone, _ */

define([
    "jquery",
    "doTimeout",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/EventManager"
], (
    $,
    doTimeout,
    constants,
    eventManager) => {

    $.doTimeout = function (name, time, func) {
        func(); // run the function immediately rather than delayed.
    };

    return function (server) {

        eventManager.registerListener(constants.EVENT_APP_INITIALIZED, () => {
            // The test suites and their dependencies resolve through the require.config of main.js, so they are
            // only loaded here, once the application is up. ViewManager and Configuration are requested explicitly:
            // the application itself loads ViewManager asynchronously while navigating to its first view, so a
            // synchronous require() from testStart is not guaranteed to find it loaded yet.
            require([
                "ThemeManager",
                "org/forgerock/commons/ui/common/main/ViewManager",
                "org/forgerock/commons/ui/common/main/Configuration",
                "../test/tests/OpenIGValidatorsTests",
                "../test/tests/TransformServiceTests",
                "../test/tests/DataFilterTests",
                "../test/tests/getLoggedUser"
            ], (
                ThemeManager,
                ViewManager,
                Configuration,
                openIGValidatorsTests,
                transformServiceTests,
                dataFilterTests,
                getLoggedUser) => {
                ThemeManager.getTheme().then(() => {
                    QUnit.testStart((testDetails) => {
                        console.log(`Starting ${testDetails.module}: ${testDetails.name}`);

                        ViewManager.currentView = null;
                        ViewManager.currentDialog = null;
                        ViewManager.currentViewArgs = null;
                        ViewManager.currentDialogArgs = null;

                        Configuration.baseTemplate = null;
                    });

                    _.delay(() => {
                        openIGValidatorsTests.executeAll(server, getLoggedUser());
                        transformServiceTests.executeAll(server);
                        dataFilterTests.executeAll(server);
                        QUnit.start();
                    }, 500);

                    QUnit.done(() => {
                        localStorage.clear();
                        Backbone.history.stop();
                        window.location.hash = "";
                    });
                });
            });
        });
    };

});
