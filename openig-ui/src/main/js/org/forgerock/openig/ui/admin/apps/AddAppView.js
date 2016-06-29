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
    "form2js",
    "org/forgerock/openig/ui/admin/apps/AbstractAppView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/main/ValidatorsManager",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/openig/ui/admin/delegates/AppDelegate",
    "org/forgerock/openig/ui/admin/util/AppsUtils",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openig/ui/admin/models/AppModel",
    "org/forgerock/openig/ui/admin/models/AppsCollection"
], function (
    $,
    _,
    form2js,
    AbstractAppView,
    eventManager,
    validatorsManager,
    constants,
    AppDelegate,
    appsUtils,
    router,
    AppModel,
    AppsCollection) {
    var AddEditAppView = AbstractAppView.extend({
        template: "templates/openig/admin/apps/AddAppTemplate.html",
        events: {
            "click #submitApp": "appFormSubmit",
            "onValidate": "onValidate"
        },
        data: {

        },
        app: null,

        render: function (args, callback) {
            var appId;
            this.data = {};
            this.data.docHelpUrl = constants.DOC_URL;
            appId = args[0];

            // editState true for readonly
            this.data.editState = false;

            this.app = new AppModel();
            // TODO: check duplicate from url/router
            if (appId !== undefined) {
                AppsCollection.byId(appId).then(_.bind(function (parentApp) {
                    this.data.appName = parentApp.get("content/name");
                    this.data.appUrl = parentApp.get("content/url");
                    this.data.appCondition = parentApp.get("content/condition");
                    this.app.attributes.content = JSON.parse(JSON.stringify(parentApp.attributes.content));

                    this.parentRender(_.bind(function () {
                        validatorsManager.bindValidators(this.$el);
                        this.loadAppTemplate(callback);
                    }, this));

                }, this));
            } else {
                this.data.appName = "";
                this.data.appUrl = "";
                this.data.appCondition = "";

                this.parentRender(_.bind(function () {
                    validatorsManager.bindValidators(this.$el);
                    this.loadAppTemplate(callback);
                }, this));
            }
        },

        appFormSubmit: function (event) {
            var newAppId,
                form = this.$el.find("#appForm")[0],
                formVal;

            event.preventDefault();

            if (this.app && this.app !== null) {
                // Parse Form values
                formVal = form2js(form, ".", true);
                // Create simple content + fake id
                newAppId = formVal.name + Date.now();

                _.extend(formVal, { id: newAppId });
                this.app.set({
                    _id: newAppId,
                    content: _.extend(this.app.get("content"), formVal)
                });

                if (!this.app.isValid()) {
                    $(form).find("input").trigger("validate");
                    return;

                }

                this.app.save();
                AppsCollection.add([
                    this.app
                ]);

                router.navigate("apps/edit/" + newAppId + "/", true);

            }
        }

    });

    return new AddEditAppView();
});
