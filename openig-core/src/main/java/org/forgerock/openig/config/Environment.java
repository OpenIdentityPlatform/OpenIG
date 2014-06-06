/*
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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.openig.config;

import java.io.File;
import java.util.Locale;

import javax.servlet.ServletContext;

import org.forgerock.openig.resource.ResourceException;

/**
 * Represents the application environment and provides methods for obtaining the
 * instance path, configuration path, and other directories. The application
 * instance directory is determined during application initialization and
 * depends on whether the application is running as a standalone application or
 * as a web application inside a container. For standalone applications, the
 * instance directory is the directory in which the application has been
 * installed. For web applications, the instance directory is computed by this
 * class as either
 * <tt><strong>$AppData/</strong><em>product</em><strong>/</strong></tt> if the
 * <strong>{@code $AppData}</strong> environment variable is defined (typical in
 * Windows installations), or otherwise
 * <tt><em>user-home</em><strong>/.</strong><em>product</em><strong>/</strong></tt>
 * otherwise (typical in Unix installations).
 */
public final class Environment {
    /**
     * Creates a new environment for a standalone application which has been
     * installed in {@code instanceRoot}.
     *
     * @param instanceRoot The application installation directory.
     * @return A new environment for a standalone application.
     */
    public static Environment forStandaloneApp(final String instanceRoot) {
        return new Environment(new File(instanceRoot).getAbsoluteFile());
    }

    /**
     * Creates a new environment for a web application having the provided
     * product name.
     *
     * @param productName The name of the product.
     * @return A new environment for a web application.
     */
    public static Environment forWebApp(final String productName) {
        return new Environment(getProductHomeDirectory(productName).getAbsoluteFile());
    }

    private static File getProductHomeDirectory(final String productName) {
        final StringBuilder sb = new StringBuilder();
        final String appData = System.getenv("AppData");
        if (appData != null) {
            // windoze
            sb.append(appData);
            sb.append(File.separatorChar);
        } else {
            // eunuchs
            sb.append(System.getProperty("user.home"));
            sb.append(File.separatorChar);
            sb.append('.');
        }
        sb.append(productName.toLowerCase(Locale.ENGLISH));
        return new File(sb.toString());
    }

    private final File configDir;
    private final File instanceRoot;
    private final File scriptsDir;

    private Environment(final File instanceRoot) {
        this.instanceRoot = instanceRoot;
        this.configDir = new File(instanceRoot, "config");
        this.scriptsDir = new File(instanceRoot, "scripts");
    }

    /**
     * Returns the name of the directory containing the configuration file. Note
     * that web applications may have per-instance configuration. See
     * {@link ConfigResource} for more information.
     *
     * @return The name of the directory containing the configuration file.
     */
    public File getConfigDir() {
        return configDir;
    }

    /**
     * Creates a new configuration resource, with a path based on the provided
     * servlet context.
     *
     * @param context The servlet context from which the product instance name can
     * be derived.
     * @return A new configuration resource.
     * @throws ResourceException If the configuration (or bootstrap) resource could not be
     * found.
     */
    public ConfigResource getConfigResource(final ServletContext context) throws ResourceException {
        return getConfigResource(context.getRealPath("/"));
    }

    /**
     * Creates a new configuration resource, with a path based on the provided
     * instance name.
     *
     * @param instance The product instance name.
     * @return A new configuration resource.
     * @throws ResourceException If the configuration (or bootstrap) resource could not be
     * found.
     */
    public ConfigResource getConfigResource(final String instance) throws ResourceException {
        return new ConfigResource(this, instance);
    }

    /**
     * Returns the application instance directory containing configuration and
     * scripts.
     *
     * @return The application instance directory containing configuration and
     * scripts.
     */
    public File getInstanceRoot() {
        return instanceRoot;
    }

    /**
     * Returns the name of the directory containing scripts for the provided
     * scripting language.
     *
     * @param language The scripting language, e.g. "groovy".
     * @return The name of the directory containing scripts for the provided
     * scripting language.
     */
    public File getScriptsDir(final String language) {
        return new File(scriptsDir, language);
    }
}
