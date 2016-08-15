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

require.config({
    map: {
        "*": {
            "Footer": "org/forgerock/openig/ui/common/components/Footer",
            "ThemeManager": "org/forgerock/openig/ui/common/util/ThemeManager",
            "LoginView": "org/forgerock/commons/ui/common/LoginView",
            "ForgotUsernameView": "org/forgerock/commons/ui/user/anonymousProcess/ForgotUsernameView",
            "PasswordResetView": "org/forgerock/commons/ui/user/anonymousProcess/PasswordResetView",
            "LoginDialog": "org/forgerock/commons/ui/common/LoginDialog",
            "RegisterView": "org/forgerock/commons/ui/user/anonymousProcess/SelfRegistrationView",
            "NavigationFilter": "org/forgerock/commons/ui/common/components/navigation/filters/RoleFilter",
            "KBADelegate": "org/forgerock/commons/ui/user/delegates/KBADelegate",
            // TODO: Remove this when there are no longer any references to the "underscore" dependency
            "underscore": "lodash"
        }
    },
    paths: {
        // sinon only needed (or available) for Mock project
        sinon: "libs/sinon-1.15.4",
        i18next: "libs/i18next-1.7.3-min",
        backbone: "libs/backbone-1.1.2-min",
        "backbone.paginator": "libs/backbone.paginator.min-2.0.2-min",
        "backbone-relational": "libs/backbone-relational-0.9.0-min",
        "backgrid": "libs/backgrid.min-0.3.5-min",
        "backgrid-filter": "libs/backgrid-filter.min-0.3.5-min",
        "backgrid-paginator": "libs/backgrid-paginator.min-0.3.5-min",
        selectize: "libs/selectize-0.12.1-min",
        lodash: "libs/lodash-3.10.1-min",
        js2form: "libs/js2form-2.0-769718a",
        form2js: "libs/form2js-2.0-769718a",
        spin: "libs/spin-2.0.1-min",
        jquery: "libs/jquery-2.1.1-min",
        xdate: "libs/xdate-0.8-min",
        doTimeout: "libs/jquery.ba-dotimeout-1.0-min",
        handlebars: "libs/handlebars-4.0.5",
        moment: "libs/moment-2.8.1-min",
        bootstrap: "libs/bootstrap-3.3.5-custom",
        "bootstrap-dialog": "libs/bootstrap-dialog-1.34.4-min",
        placeholder: "libs/jquery.placeholder-2.0.8",
        dragula: "libs/dragula-3.6.7-min",
        d3: "libs/d3-3.5.5-min",
        dimple: "libs/dimple-2.1.2-min"
    },

    shim: {
        sinon: {
            exports: "sinon"
        },
        underscore: {
            exports: "_"
        },
        backbone: {
            deps: ["underscore"],
            exports: "Backbone"
        },
        "backbone.paginator": {
            deps: ["backbone"]
        },
        "backgrid": {
            deps: ["jquery", "underscore", "backbone"],
            exports: "Backgrid"
        },
        "backgrid-filter": {
            deps: ["backgrid"]
        },
        "backgrid-paginator": {
            deps: ["backgrid", "backbone.paginator"]
        },
        js2form: {
            exports: "js2form"
        },
        form2js: {
            exports: "form2js"
        },
        spin: {
            exports: "spin"
        },
        bootstrap: {
            deps: ["jquery"]
        },
        "bootstrap-dialog": {
            deps: ["jquery", "underscore", "backbone", "bootstrap"]
        },
        placeholder: {
            deps: ["jquery"]
        },
        selectize: {
            deps: ["jquery"]
        },
        xdate: {
            exports: "xdate"
        },
        doTimeout: {
            deps: ["jquery"],
            exports: "doTimeout"
        },
        i18next: {
            deps: ["jquery", "handlebars"],
            exports: "i18n"
        },
        moment: {
            exports: "moment"
        },
        d3: {
            exports: "d3"
        },
        dimple: {
            exports: "dimple",
            deps: ["d3"]
        }
    }
});

require([
    // This list should be all of the things that you either need to use to initialize
    // prior to starting, or should be the modules that you want included in the minified
    // startup bundle. Be sure to only put things in this list that you really need to have
    // loaded on startup (so that you get the benefit of minification without adding more
    // than you really need for the first load)

    // These are used prior to initialization. Note that the callback function names
    // these as arguments, but ignores the others.
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/util/CookieHelper",
    "org/forgerock/openig/ui/common/main/LocalStorage",

    // core forgerock-ui files
    "org/forgerock/commons/ui/common/main",

    // files that are necessary for rendering the login page for forgerock-ui-openig
    "org/forgerock/openig/ui/main",
    "config/main",

    // libraries necessary for forgerock-ui (and thus worth bundling)
    "jquery",
    "underscore",
    "backbone",
    "handlebars",
    "i18next",
    "spin"
], (EventManager, Constants, CookieHelper, LocalStorage) => {

    // Mock project is run without server. Framework requires cookies to be enabled in order to be able to login.
    // Default CookieHelper.cookiesEnabled() implementation will always return false as cookies cannot be set from local
    // file. Hence redefining function to return true
    CookieHelper.cookiesEnabled = function () {
        return true;
    };

    // Adding stub user
    LocalStorage.add("mock/repo/internal/user/test", {
        _id: "test",
        _rev: "1",
        component: "'mock/repo/internal/user",
        roles: ["ui-admin", "ui-user", "ui-self-service-user"],
        uid: "test",
        userName: "test",
        password: "test",
        telephoneNumber: "12345",
        givenName: "Jack",
        sn: "White",
        mail: "white@test.com",
        kbaInfo: [
            {
                "customQuestion": "What is my favorite open source identity company?",
                "answer": {
                    "$crypto": {
                        "value":
                        {
                            "algorithm": "SHA-256",
                            "data": "LbOwzJnSKtSn2waBA/6Zv8AFaTwe74vHh9dyPaBOVnZFTCU/MsNWTfmbRcx2PM4d"
                        },
                        "type": "salted-hash"
                    }
                }
            },
            {
                "questionId": "1",
                "answer": {
                    "$crypto": {
                        "value":
                        {
                            "algorithm": "SHA-256",
                            "data": "ht5QecQ11l4mnCBxa8TKRU7KZhhMrD6SSTxv1XJkbEUlRjGhw5Ss5WMC4diBgNme"
                        },
                        "type": "salted-hash"
                    }
                }
            }
        ]
    });

    EventManager.sendEvent(Constants.EVENT_DEPENDENCIES_LOADED);
});
