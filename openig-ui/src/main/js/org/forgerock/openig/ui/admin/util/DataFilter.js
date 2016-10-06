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
    "lodash"
], (
    _
) => (
    class DataFilter {
        // Set text or conditions filter is looking for
        // value could contains plain text and/or key with value
        // ie.: "plain text key: value"
        constructor (value, searchableParams) {
            if (!value && value.length === 0) {
                this.searchConditions = {};
                return;
            }
            if (searchableParams) {
                this.searchable = searchableParams;
            }
            this.searchConditions = DataFilter.parseSearch(value);
        }

        // Get array of object keys where to search
        get searchableParams () {
            return this.searchable;
        }

        // Set array of object keys where to search
        set searchableParams (value) {
            this.searchable = value;
        }

        // Parse text into search conditions
        static parseSearch (text) {
            text = text.toLowerCase();
            const search = {
                "*": []
            };

            const regex = /(\b(?!http|https\b)\w+:)| /g;
            let lastKey = ["*:"];
            let lastValStart = 0;

            const addValue = (newValue) => {
                const newKey = lastKey[0].slice(0, lastKey[0].length - 1);
                newValue = newValue.trim();
                if (!newValue || newValue === "") {
                    return;
                }
                if (newKey === "" || newKey === "*") {
                    search["*"].push(newValue);
                } else {
                    const existingValue = search[newKey];
                    if (!existingValue) {
                        search[newKey] = [newValue];
                    } else {
                        search[newKey].push(newValue);
                    }
                }
            };

            let key = regex.exec(text);
            while (key !== null) {
                if (key.index === regex.lastIndex) {
                    regex.lastIndex++;
                }
                addValue(text.slice(lastValStart, key.index));

                lastKey = key;
                lastValStart = regex.lastIndex;
                key = regex.exec(text);
            }
            addValue(text.slice(lastValStart));
            return search;
        }

        static isValue (value) {
            return _.isNumber(value) || _.isString(value) || _.isDate(value) || _.isBoolean(value);
        }

        static containsFilter (value, filter) {
            return DataFilter.isValue(value) && (value.toString().toLowerCase().indexOf(filter) >= 0);
        }

        static startWithFilter (value, filter) {
            return DataFilter.isValue(value) && value && value.toString().toLowerCase().indexOf(filter) === 0;
        }

        matchRules (data) {
            let match = true;
            _.forOwn(this.searchConditions, (searchValue, searchKey) => {
                if (searchKey === "*") {
                    // simple text match when any attribute contains text
                    if (searchValue.length > 0) {
                        _.each(searchValue, (oneSearchValue) => {
                            match = match && _.toArray(data)
                                .filter((value) => DataFilter.containsFilter(value, oneSearchValue)).length > 0;
                        });
                    }
                } else {
                    // attribute match when attribute's value start with text
                    _.each(searchValue, (oneSearchValue) => {
                        match = match && DataFilter.startWithFilter(data[searchKey], oneSearchValue);
                    });
                }
            });
            return match;
        }

        // Filter compare data object with search conditions
        filter (data) {
            if (!this.searchConditions) {
                return true;
            }
            if (this.searchable) {
                data = _.pick(data, this.searchable);
            }
            return this.matchRules(data);
        }
    }
));
