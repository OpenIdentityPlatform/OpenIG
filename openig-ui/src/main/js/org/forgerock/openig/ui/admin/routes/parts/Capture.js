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

define(
    [
        "lodash",
        "form2js",
        "i18next",
        "org/forgerock/commons/ui/common/main/AbstractView",
        "org/forgerock/openig/ui/admin/util/FormUtils",
        "org/forgerock/commons/ui/common/main/EventManager",
        "org/forgerock/commons/ui/common/util/Constants"
    ],
    (_,
     form2js,
     i18n,
     AbstractView,
     FormUtils,
     EventManager,
     Constants) => (
        AbstractView.extend(
            {
                element: ".main",
                template: "templates/openig/admin/routes/parts/Capture.html",
                partials: [
                    "templates/openig/admin/common/form/SliderControl.html",
                    "templates/openig/admin/common/form/GroupControl.html",
                    "templates/openig/admin/routes/components/FormFooter.html"
                ],
                events: {
                    "click .js-reset-btn": "resetClick",
                    "click .js-save-btn": "saveClick",
                    "change .checkbox-slider input[type='checkbox']": "onToggleSwitch"
                },
                data: {
                    formId: "capture-form"
                },
                initialize (options) {
                    this.data = _.extend(this.data, options.parentData);
                },

                render () {
                    const capture = this.findCapture();

                    this.data.controls = [
                        {
                            name: "inboundGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "inboundRequest",
                                    value: capture.inbound.request,
                                    controlType: "slider"
                                },
                                {
                                    name: "inboundResponse",
                                    value: capture.inbound.response,
                                    controlType: "slider"
                                }
                            ]
                        },
                        {
                            name: "outboundGroup",
                            controlType: "group",
                            controls: [
                                {
                                    name: "outboundRequest",
                                    value: capture.outbound.request,
                                    controlType: "slider"
                                },
                                {
                                    name: "outboundResponse",
                                    value: capture.outbound.response,
                                    controlType: "slider"
                                }
                            ]
                        }
                    ];
                    FormUtils.extendControlsSettings(this.data.controls, {
                        autoTitle: true,
                        autoHint: false,
                        translatePath: "templates.routes.parts.capture.fields",
                        defaultControlType: "edit"
                    });
                    FormUtils.fillPartialsByControlType(this.data.controls);

                    this.parentRender();
                },

                resetClick (event) {
                    event.preventDefault();
                    this.render();
                },

                saveClick (event) {
                    event.preventDefault();

                    const form = this.$el.find(`#${this.data.formId}`)[0];
                    const capture = this.formToCapture(form);
                    if (this.isCaptureEnabled(capture)) {
                        this.data.routeData.set("capture", capture);
                    } else {
                        this.data.routeData.unset("capture");
                    }
                    this.data.routeData.save();

                    const submit = this.$el.find("#submit-capture");
                    submit.attr("disabled", true);

                    EventManager.sendEvent(
                        Constants.EVENT_DISPLAY_MESSAGE_REQUEST,
                        {
                            key: "routeSettingsSaveSuccess",
                            filter: i18n.t("templates.routes.parts.capture.title")
                        }
                    );
                },

                onToggleSwitch (event) {
                    event.preventDefault();

                    const form = this.$el.find(`#${this.data.formId}`)[0];
                    const submit = this.$el.find(".js-save-btn");

                    const capture = this.findCapture();
                    const newCapture = this.formToCapture(form);

                    // If captures are equal: disable the submit button, enable it otherwise
                    submit.attr("disabled", _.isEqual(capture, newCapture));
                },


                isCaptureEnabled (capture) {
                    return capture.inbound.request === true ||
                        capture.inbound.response === true ||
                        capture.outbound.request === true ||
                        capture.outbound.response === true;
                },

                findCapture () {
                    let capture = this.data.routeData.get("capture");
                    if (!capture) {
                        capture = this.defaultCapture();
                    }
                    return capture;
                },

                defaultCapture () {
                    return {
                        inbound: {
                            request: false,
                            response: false
                        },
                        outbound: {
                            request: false,
                            response: false
                        }
                    };
                },

                formToCapture (form) {
                    const formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
                    return {
                        inbound: {
                            request: formVal.inboundRequest,
                            response: formVal.inboundResponse
                        },
                        outbound: {
                            request: formVal.outboundRequest,
                            response: formVal.outboundResponse
                        }
                    };
                }
            }
        )
    )
);
