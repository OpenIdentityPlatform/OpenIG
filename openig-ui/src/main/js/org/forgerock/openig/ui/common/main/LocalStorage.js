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

/**
 * Local storage helper.
 *
 * @author Eugenia Sergueeva
 */

define([
    "lodash"
], (_) => {
    const mockPrefix = "forgerock-openig-";

    function isLocalStorageSupported () {
        return typeof localStorage !== "undefined";
    }

    if (isLocalStorageSupported()) {
        return {
            /**
             * Adds data.
             *
             * @param {string} key key
             * @param {string} data data
             * @returns {Object} newly added data
             */
            add (key, data) {
                if (!this.get(key)) {
                    console.log(`Adding item to localStorage: ${data}`);
                    localStorage.setItem(mockPrefix + key, JSON.stringify(data));
                    return key;
                }

                return null;
            },

            /**
             * Applies a patch definition object to an item in localstorage
             *
             * @param {string} key key
             * @param {string} data data
             * @returns {Object} patched data
             */
            patch (key, data) {
                const item = this.get(key);
                if (item) {

                    _.each(data, (patchEntry) => {

                        const pathParts = _.filter(patchEntry.field.split("/"), (part) => (part.length > 0));

                        let node = item;

                        _.each(pathParts, (part, index) => {
                            if (index !== (pathParts.length - 1)) {
                                if (node[part] === undefined) {
                                    node[part] = {};
                                }
                                node = node[part];
                            } else if (index === (pathParts.length - 1)) {
                                if (patchEntry.operation === "add" || patchEntry.operation === "replace") {
                                    node[part] = patchEntry.value;
                                } else if (patchEntry.operation === "remove") {
                                    if (_.isArray(node)) {
                                        node.splice(part, 1);
                                    } else {
                                        delete node[part];
                                    }
                                }
                            }
                        });

                    });

                    localStorage.setItem(mockPrefix + key, JSON.stringify(item));
                }
                return item;
            },

            /**
             * Gets data by key.
             *
             * @param {string} key key
             * @returns {Object} data
             */
            get (key) {
                return JSON.parse(localStorage.getItem(mockPrefix + key));
            },

            /**
             * Removes data by key.
             *
             * @param {string} key key
             * @returns {boolean} whether data was removed
             */
            remove (key) {
                return delete localStorage[mockPrefix + key];
            }
        };

    } else {

        return {
            add () {
                console.log("LocalStorage is not supported");
            },

            patch () {
                console.log("LocalStorage is not supported");
            },

            get () {
                console.log("LocalStorage is not supported");
            },

            remove () {
                console.log("LocalStorage is not supported");
            }
        };
    }
});
