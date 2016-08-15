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
    "org/forgerock/commons/ui/common/main/AbstractModel",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/openig/ui/common/main/LocalStorage",
    "org/forgerock/commons/ui/common/util/ObjectUtil"
], (
    $,
    _,
    AbstractModel,
    Constants,
    EventManager,
    LocalStorage,
    ObjectUtil) => {
    const mockPrefix = "mock/repo/internal/user/";
    const UserModel = AbstractModel.extend({
        // sync has to be overridden to work with localstorage; products using CREST backend shouldn't need to do so
        sync (method, model) {
            switch (method) {
                case "read":
                    model.set(LocalStorage.get(mockPrefix + model.id));
                    return $.Deferred().resolve(model.toJSON());

                case "patch":
                    const deferred = $.Deferred();

                    // if any protected attributes have changed, but the current password is incorrect...
                    if (_.any(model.getProtectedAttributes(), (protectedAttribute) => {
                        const hasAttr = _.has(model.changedAttributes(), protectedAttribute);
                        return hasAttr;
                    }) &&
                        // normally this 'currentPassword' check would be done on the backend, of course.
                        // In the mock we do it in memory
                        (!_.has(model, "currentPassword") || model.currentPassword !== model.hidden.password)
                    ) { // then reset the model and display the failure message
                        const previous = model.previousAttributes();
                        model.clear();
                        model.set(previous);
                        EventManager.sendEvent(Constants.EVENT_DISPLAY_MESSAGE_REQUEST, "userProfileIncorrectPassword");
                        deferred.reject(model.toJSON());
                    } else {
                        // either no protected attributes have changed,
                        // or the proper current password was provided, so make the change
                        LocalStorage.patch(
                            mockPrefix + model.id,
                            ObjectUtil.generatePatchSet(model.toJSON(),
                            model.previousAttributes())
                        );
                        model.set(LocalStorage.get(mockPrefix + model.id));
                        deferred.resolve(model.toJSON());
                    }
                    // password is a private attribute, it shouldn't stay as a visible attribute in the model
                    model.hideAttribute("password");
                    // always remove the currentPassword so that subsequent requests don't accidentally retain it
                    delete model.currentPassword;
                    return deferred.promise();

            }
        },
        protectedAttributeList: ["password"],
        hidden: {},
        hideAttribute (attr) {
            if (this.has(attr)) {
                this.hidden[attr] = this.get(attr);
            }
            this.unset(attr);
        },
        getProfile (username, password) {
            this.id = username;
            return this.fetch().then(() => {
                this.uiroles = this.get("roles");
                if (this.get("password") === password) {
                    this.hideAttribute("password");
                    return this;
                } else {
                    return $.Deferred().reject();
                }
            });
        },
        getProtectedAttributes () {
            return this.protectedAttributeList;
        },
        setCurrentPassword (currentPassword) {
            this.currentPassword = currentPassword;
        }
    });
    return new UserModel();
});
