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

/* global module, require */

module.exports = function (grunt) {
    grunt.loadNpmTasks("grunt-babel");
    grunt.loadNpmTasks("grunt-contrib-copy");
    grunt.loadNpmTasks("grunt-contrib-less");
    grunt.loadNpmTasks("grunt-contrib-qunit");
    grunt.loadNpmTasks("grunt-contrib-requirejs");
    grunt.loadNpmTasks("grunt-contrib-watch");
    grunt.loadNpmTasks("grunt-eslint");
    grunt.loadNpmTasks("grunt-notify");
    grunt.loadNpmTasks("grunt-sync");
    grunt.loadNpmTasks("grunt-serve");

    var targetDirectory = "target/www",
        testTargetDirectory = "target/test",
        compositionDirectory = "target/compose",
        testCompositionDirectory = "target/testcompose",
        nodeModules = "node_modules",
        watchDirs = [
            "src/main/js",
            "src/main/resources"
        ],
        testWatchDirs = [
            "src/test/js",
            "src/test/resources"
        ],
        transpiledFiles = [
            "**/*.js"
        ],
        staticFiles = [
            "**/*",
            "!**/*.less",
            "!**/*.js"
        ];

    grunt.initConfig({
        eslint: {
            /**
             * Check the JavaScript source code for common mistakes and style issues.
             */
            lint: {
                src: [
                    "src/main/js/**/*.js",
                    "src/test/js/**/*.js"
                ],
                options: {
                    format: require.resolve("eslint-formatter-warning-summary")
                }
            }
        },
        less: {
            /**
             * Compile LESS source code into minified CSS files.
             */
            compile: {
                files: [{
                    src: compositionDirectory + "/css/structure.less",
                    dest: targetDirectory + "/css/structure.css"
                },
                {
                    src: compositionDirectory + "/css/theme.less",
                    dest: targetDirectory + "/css/theme.css"
                }],
                options: {
                    compress: true,
                    plugins: [
                        new (require("less-plugin-clean-css"))({})
                    ],
                    relativeUrls: true
                }
            }

        },
        qunit: {
            all: [testTargetDirectory + "/qunit.html"]
        },
        requirejs: {
            /**
             * Concatenate and uglify the JavaScript.
             */
            compile: {
                options: {
                    baseUrl: compositionDirectory,
                    mainConfigFile: compositionDirectory + "/main.js",
                    out: targetDirectory + "/main.js",
                    include: ["main"],
                    preserveLicenseComments: false,
                    generateSourceMaps: true,
                    optimize: "uglify2",
                    excludeShallow: [
                        // These files are excluded from optimization so that the UI can be customized without having to
                        // repackage it.
                        "config/AppConfiguration",

                        // Exclude mock project dependencies to create a more representative bundle.
                        "mock/Data",
                        "sinon"
                    ]
                }
            }
        },
        notify_hooks: {
            options: {
                enabled: true,
                title: "ForgeRock UI QUnit Tests"
            }
        },
        /**
         * Sync is used during development.
         */
        sync: {
            /**
             * Copy all the sources and resources from this project to compose folder
             */
            compose: {
                files: watchDirs.map(function (dir) {
                    return {
                        cwd: dir,
                        src: ["**"],
                        dest: compositionDirectory
                    };
                }),
                verbose: false,
                compareUsing: "md5"
            },
            /**
             * Copy all the test sources and resources from this project to compose folder
             */
            composetest: {
                files: testWatchDirs.map(function (dir) {
                    return {
                        cwd: dir,
                        src: ["**"],
                        dest: testCompositionDirectory
                    };
                }),
                verbose: false,
                compareUsing: "md5"
            },
            /**
             * Copy static files from compose folder to target
             */
            staticfiles: {
                files: [{
                    cwd: compositionDirectory,
                    src: staticFiles,
                    dest: targetDirectory
                }],
                verbose: true,
                compareUsing: "md5"
            },
             /**
             * Copy static test files from compose folder to target
             */
            test: {
                files: testWatchDirs.map(function (dir) {
                    return {
                        cwd: dir,
                        src: staticFiles,
                        dest: testTargetDirectory
                    };
                }),
                verbose: true,
                compareUsing: "md5"
            },
            transpiledfiles: {
                files: [{
                    cwd: compositionDirectory,
                    src: transpiledFiles,
                    dest: targetDirectory
                }],
                verbose: true,
                compareUsing: "md5"
            },
            transpiledtestfiles: {
                files: [{
                    cwd: testCompositionDirectory,
                    src: transpiledFiles,
                    dest: testTargetDirectory
                }],
                verbose: true,
                compareUsing: "md5"
            }
        },
        watch: {
            /**
             * Redeploy whenever any source files change.
             */
            source: {
                files: watchDirs.map(function (dir) {
                    return dir + "/**";
                }),
                tasks: ["build-dev"]
            },
            test: {
                files: testWatchDirs.map(function (dir) {
                    return dir + "/**";
                }),
                tasks: ["build-dev"]
            }
        },
        serve: {
            options: {
                livereload: true,
                serve: {
                    path: targetDirectory
                },
                port: 9000
            }
        },
        babel: {
            options: {
                env: {
                    development: {
                        sourceMaps: true
                    }
                },
                ignore: ["libs/"],
                presets: ["es2015"]
            },
            source: {
                files: watchDirs.map(function (dir) {
                    return {
                        expand: true,
                        cwd: dir,
                        src: transpiledFiles,
                        dest: compositionDirectory
                    };
                })
            },
            test: {
                files: testWatchDirs.map(function (dir) {
                    return {
                        expand: true,
                        cwd: dir,
                        src: transpiledFiles,
                        dest: testCompositionDirectory
                    };
                })
            }
        },
        copy: {
            dependencies: {
                files: [{
                    expand: true,
                    cwd: nodeModules + "/awesome-bootstrap-checkbox",
                    src: ["*.css"],
                    dest: compositionDirectory + "/css"
                }]
            }
        }
    });

    grunt.registerTask("build", [
        "eslint",
        "sync:compose",
        "copy:dependencies",
        "sync:staticfiles",
        "less",
        "sync:test",
        "sync:composetest",
        "babel",
        "sync:transpiledfiles",
        "sync:transpiledtestfiles",
        //"qunit",
        "requirejs"
    ]);

    grunt.registerTask("build-dev", [
        "eslint",
        "sync:compose",
        "copy:dependencies",
        "sync:staticfiles",
        "less",
        "sync:test",
        "sync:composetest",
        "babel",
        "sync:transpiledfiles",
        "sync:transpiledtestfiles",
        "qunit"
    ]);

    grunt.registerTask("dev", ["build-dev", "watch"]);
    grunt.registerTask("web", ["serve"]); // use Serve module for local development
    grunt.registerTask("default", "dev");

    grunt.task.run("notify_hooks");
};
