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
    "dimple",
    "org/forgerock/openig/ui/admin/apps/AbstractAppView"
], function (
    $,
    _,
    form2js,
    dimple,
    AbstractAppView
) {
    return AbstractAppView.extend({
        element: ".main",
        template: "templates/openig/admin/apps/parts/Monitoring.html",
        events: {
        },
        data: {

        },
        render: function () {
            var svg,
                data,
                myChart;

            this.parentRender(_.bind(function () {
                var x;

                this.model = {};


                svg = dimple.newSvg("#area-chart", 746, 260);
                data = [
                    { "date": new Date(2016, 5, 1, 4, 0), "trafic": 0 },
                    { "date": new Date(2016, 5, 1, 4, 10), "trafic": 50 },
                    { "date": new Date(2016, 5, 1, 4, 30), "trafic": 750 },
                    { "date": new Date(2016, 5, 1, 4, 40), "trafic": 650 },
                    { "date": new Date(2016, 5, 1, 4, 50), "trafic": 950 },
                    { "date": new Date(2016, 5, 1, 5, 0), "trafic": 800 }

                ];
                /* eslint-disable */
                // dimple.chart is not passing new-cap rule
                myChart = new dimple.chart(svg, data);
                /* eslint-enable */
                x = myChart.addCategoryAxis("x", "date");
                x.addOrderRule("date");
                myChart.addMeasureAxis("y", "trafic");

                myChart.addSeries(null, dimple.plot.area);
                myChart.draw();


            }, this));
        }
    });
});
