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
        "org/forgerock/openig/ui/admin/routes/AbstractRouteView",
        "org/forgerock/openig/ui/admin/util/FormUtils"
    ],
    (_,
     form2js,
     i18n,
     AbstractRouteView,
     FormUtils
) => (
        class Capture extends AbstractRouteView {
            constructor (options) {
                super();
                this.data = _.extend({ formId: "capture-form" }, options.parentData);
                this.settingTitle = i18n.t("templates.routes.parts.capture.title");
            }

            get template () {
                return "templates/openig/admin/routes/parts/Capture.html";
            }

            get partials () {
                return [
                    "templates/openig/admin/common/form/SliderControl.html",
                    "templates/openig/admin/common/form/GroupControl.html",
                    "templates/openig/admin/routes/components/FormFooter.html"
                ];
            }

            get events () {
                return {
                    "click .js-reset-btn": "resetClick",
                    "click .js-save-btn": "saveClick",
                    "change .checkbox-slider input[type='checkbox']": "onToggleSwitch"
                };
            }

            render () {
                const capture = this.findCapture();

                this.data.controls = [
                    {
                        name: "entityGroup",
                        controlType: "group",
                        controls: [
                            {
                                name: "entity",
                                value: capture.entity,
                                controlType: "slider",
                                hint: "templates.routes.parts.capture.fields.hint"
                            }
                        ]
                    },
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
            }

            saveClick (event) {
                event.preventDefault();

                const form = this.$el.find(`#${this.data.formId}`)[0];
                const capture = this.formToCapture(form);
                if (this.isCaptureEnabled(capture)) {
                    this.data.routeData.set("capture", capture);
                } else {
                    this.data.routeData.unset("capture");
                }
                this.data.routeData.save()
                        .then(
                            () => {
                                const submit = this.$el.find(".js-save-btn");
                                submit.attr("disabled", true);
                                this.showNotification(this.NOTIFICATION_TYPE.SaveSuccess);
                            },
                            () => {
                                this.showNotification(this.NOTIFICATION_TYPE.SaveFailed);
                            }
                        );
            }

            onToggleSwitch (event) {
                event.preventDefault();

                const form = this.$el.find(`#${this.data.formId}`)[0];
                const submit = this.$el.find(".js-save-btn");

                const capture = this.findCapture();
                const newCapture = this.formToCapture(form);

                    // If captures are equal: disable the submit button, enable it otherwise
                submit.attr("disabled", _.isEqual(capture, newCapture));
            }

            isCaptureEnabled (capture) {
                return capture.entity === true ||
                        capture.inbound.request === true ||
                        capture.inbound.response === true ||
                        capture.outbound.request === true ||
                        capture.outbound.response === true;
            }

            findCapture () {
                let capture = this.data.routeData.get("capture");
                if (!capture) {
                    capture = this.defaultCapture();
                }
                return capture;
            }

            defaultCapture () {
                return {
                    entity: false,
                    inbound: {
                        request: false,
                        response: false
                    },
                    outbound: {
                        request: false,
                        response: false
                    }
                };
            }

            formToCapture (form) {
                const formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
                return {
                    entity: formVal.entity,
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
		})
    );
